package ru.bl3xand.pancake.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.data.model.NoteItem
import ru.bl3xand.pancake.data.model.notes.NOTE_COMPARATOR
import ru.bl3xand.pancake.data.model.notes.NoteColors
import ru.bl3xand.pancake.data.repository.GitHubImageSyncRepository
import ru.bl3xand.pancake.data.repository.SyncQueueRepository
import ru.bl3xand.pancake.data.sync.GitHubDeleteQueueSyncEngine
import ru.bl3xand.pancake.utils.logs.Logger
import ru.bl3xand.pancake.utils.preferences.SpacePathHelper
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.security.SpaceCrypto
import ru.bl3xand.pancake.utils.user.UserNameNormalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesFragmentViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NotesFragmentViewModel"
    }

    private val _notes = MutableLiveData<List<NoteItem>>()
    val notes: LiveData<List<NoteItem>> get() = _notes

    private val database: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child(
            SpacePathHelper.node(application, AppConfig.Firebase.NOTES)
        )

    private val prefs by lazy { getAppPreferences(application) }
    private val spaceId: String by lazy {
        SpacePathHelper.currentSpaceId(application)
            ?: error("Space is not selected")
    }

    private val dateFormat = SimpleDateFormat("dd/M/yyyy HH:mm:ss", Locale.getDefault())
    private val githubImageSync by lazy { GitHubImageSyncRepository(application.applicationContext) }
    private val syncQueue by lazy { SyncQueueRepository(application.applicationContext) }
    private fun currentUserName(): String =
        UserNameNormalizer.normalize(
            prefs.getString(AppConfig.Preferences.CHARACTER_KEY, AppConfig.Characters.DEFAULT)
        )

    private val notesListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            _notes.value = snapshot.children
                .mapNotNull { it.toNoteItemEncrypted(spaceId) }
                .sortedWith(NOTE_COMPARATOR)
        }

        override fun onCancelled(error: DatabaseError) {
            Logger.logError(TAG, "loadNotes error: $error")
        }
    }

    init {
        database.addValueEventListener(notesListener)
    }

    fun createNoteId(): String? = database.push().key

    fun addNote(
        noteId: String? = null,
        title: String,
        noteText: String,
        color: String,
        imagePaths: List<String>,
        webLink: String,
        dateTime: String
    ) {
        val id = noteId ?: database.push().key ?: return
        val now = System.currentTimeMillis()
        val nextOrder = (_notes.value?.maxOfOrNull { it.sortOrder } ?: -1L) + 1L
        val note = NoteItem(
            id = id,
            title = title,
            noteText = noteText,
            color = color,
            imgPath = imagePaths.firstOrNull().orEmpty(),
            imagePaths = imagePaths,
            webLink = webLink,
            dateTime = dateTime.ifBlank { dateFormat.format(Date(now)) },
            createdBy = currentUserName(),
            updatedBy = currentUserName(),
            timestamp = now,
            updatedAt = now,
            sortOrder = nextOrder,
            isPinned = false
        )
        database.child(id).setValue(SpaceCrypto.encryptModel(spaceId, note))
    }

    fun updateNote(note: NoteItem) {
        viewModelScope.launch {
            val previousPaths = _notes.value?.firstOrNull { it.id == note.id }?.imagePaths.orEmpty()
            val removedFromGitHub = previousPaths.filter { old ->
                old.startsWith("https://raw.githubusercontent.com/") &&
                        note.imagePaths.none { it == old }
            }
            if (removedFromGitHub.isNotEmpty()) {
                val failedRepoPaths = githubImageSync.deleteImages(removedFromGitHub)
                enqueueFailedDeletes(failedRepoPaths)
            }
        }

        val now = System.currentTimeMillis()
        database.child(note.id).setValue(
            SpaceCrypto.encryptModel(
                spaceId,
                note.copy(
                    updatedAt = now,
                    updatedBy = currentUserName(),
                    dateTime = note.dateTime.ifBlank { dateFormat.format(Date(now)) }
                )
            )
        )
    }

    fun deleteNote(noteId: String) {
        val imageUrls = _notes.value?.firstOrNull { it.id == noteId }?.imagePaths.orEmpty()
            .filter { it.startsWith("https://raw.githubusercontent.com/") }
        database.child(noteId).removeValue()
        if (imageUrls.isNotEmpty()) {
            viewModelScope.launch {
                val failedRepoPaths = githubImageSync.deleteImages(imageUrls)
                enqueueFailedDeletes(failedRepoPaths)
            }
        }
    }

    fun deleteNotes(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        val imageUrls = _notes.value.orEmpty()
            .filter { it.id in noteIds }
            .flatMap { it.imagePaths }
            .filter { it.startsWith("https://raw.githubusercontent.com/") }
        database.updateChildren(noteIds.associate { it to null })
        if (imageUrls.isNotEmpty()) {
            viewModelScope.launch {
                val failedRepoPaths = githubImageSync.deleteImages(imageUrls)
                enqueueFailedDeletes(failedRepoPaths)
            }
        }
    }

    fun duplicateNotes(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        val source = _notes.value.orEmpty().filter { it.id in noteIds }
        if (source.isEmpty()) return
        val now = System.currentTimeMillis()
        var nextOrder = (_notes.value?.maxOfOrNull { it.sortOrder } ?: -1L) + 1L
        source.forEach { note ->
            val newId = database.push().key ?: return@forEach
            database.child(newId).setValue(
                SpaceCrypto.encryptModel(
                    spaceId,
                    note.copy(
                        id = newId,
                        createdBy = currentUserName(),
                        updatedBy = currentUserName(),
                        timestamp = now,
                        updatedAt = now,
                        sortOrder = nextOrder++,
                        isPinned = false
                    )
                )
            )
        }
    }

    fun togglePinned(noteIds: Set<String>, shouldPin: Boolean) {
        if (noteIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val byId = _notes.value.orEmpty().associateBy { it.id }
        noteIds.forEach { id ->
            val current = byId[id] ?: return@forEach
            val updated = current.copy(
                isPinned = shouldPin,
                updatedAt = now,
                updatedBy = currentUserName()
            )
            database.child(id).setValue(SpaceCrypto.encryptModel(spaceId, updated))
        }
    }

    fun setColor(noteIds: Set<String>, color: String) {
        if (noteIds.isEmpty() || color.isBlank()) return
        val now = System.currentTimeMillis()
        val byId = _notes.value.orEmpty().associateBy { it.id }
        noteIds.forEach { id ->
            val current = byId[id] ?: return@forEach
            val updated = current.copy(
                color = color,
                updatedAt = now,
                updatedBy = currentUserName()
            )
            database.child(id).setValue(SpaceCrypto.encryptModel(spaceId, updated))
        }
    }

    fun reorderNotes(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val byId = _notes.value.orEmpty().associateBy { it.id }
        orderedIds.forEachIndexed { index, id ->
            val current = byId[id] ?: return@forEachIndexed
            val updated = current.copy(sortOrder = index.toLong())
            database.child(id).setValue(SpaceCrypto.encryptModel(spaceId, updated))
        }
    }

    override fun onCleared() {
        database.removeEventListener(notesListener)
        super.onCleared()
    }

    private fun DataSnapshot.toNoteItemEncrypted(spaceId: String): NoteItem? {
        val parsed = SpaceCrypto.decodeSnapshot<NoteItem>(spaceId, this) ?: return null
        val id = parsed.id.ifBlank { key.orEmpty() }
        if (id.isBlank()) return null

        val now = System.currentTimeMillis()
        val safeTimestamp =
            parsed.timestamp.takeIf { it > 0L } ?: parsed.updatedAt.takeIf { it > 0L } ?: now
        val safeUpdatedAt = parsed.updatedAt.takeIf { it > 0L } ?: safeTimestamp
        val safeImages = parsed.imagePaths.filter { it.isNotBlank() }
            .ifEmpty { listOf(parsed.imgPath).filter { it.isNotBlank() } }

        return parsed.copy(
            id = id,
            timestamp = safeTimestamp,
            updatedAt = safeUpdatedAt,
            color = parsed.color.ifBlank { NoteColors.DEFAULT_MARKER },
            imgPath = safeImages.firstOrNull().orEmpty(),
            imagePaths = safeImages,
            createdBy = UserNameNormalizer.normalize(
                parsed.createdBy,
                fallback = currentUserName()
            ),
            updatedBy = UserNameNormalizer.normalize(
                parsed.updatedBy,
                fallback = UserNameNormalizer.normalize(parsed.createdBy, fallback = currentUserName())
            ),
            dateTime = parsed.dateTime.ifBlank { dateFormat.format(Date(safeTimestamp)) },
            sortOrder = parsed.sortOrder.takeIf { it >= 0L } ?: safeTimestamp,
            isPinned = parsed.isPinned
        )
    }

    private suspend fun enqueueFailedDeletes(repoPaths: List<String>) {
        if (repoPaths.isEmpty()) return
        syncQueue.enqueueDeletes(repoPaths)
        GitHubDeleteQueueSyncEngine.trigger(getApplication(), forceRetryNow = true)
    }
}