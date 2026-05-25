package ru.bl3xand.pancake.ui.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.config.AppConfig
import ru.bl3xand.pancake.data.model.CalendarItem
import ru.bl3xand.pancake.utils.logs.Logger
import ru.bl3xand.pancake.utils.preferences.SpacePathHelper
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.security.SpaceCrypto
import ru.bl3xand.pancake.utils.user.UserNameNormalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class RecurrenceType(val titleResId: Int) {
    NEVER(R.string.recurrence_never),
    DAILY(R.string.recurrence_daily),
    WEEKLY(R.string.recurrence_weekly),
    MONTHLY(R.string.recurrence_monthly),
    YEARLY(R.string.recurrence_yearly);

    companion object {
        fun fromDisplayName(application: Application, value: String): RecurrenceType =
            entries.find { application.getString(it.titleResId) == value } ?: NEVER
    }
}

class CalendarFragmentViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CalendarFragmentViewModel"
        private val COMPLETED_TASK_TTL_MS = TimeUnit.DAYS.toMillis(14)
        private const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L
        private const val CLOCK_INTERVAL_MS = 1_000L
    }

    private val _items = MutableLiveData<List<CalendarItem>>()
    val items: LiveData<List<CalendarItem>> get() = _items

    private val _currentDateTime = MutableLiveData<Pair<String, String>>()
    val currentDateTime: LiveData<Pair<String, String>> get() = _currentDateTime

    private val database: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child(
            SpacePathHelper.node(application, AppConfig.Firebase.CALENDAR)
        )

    private val prefs by lazy { getAppPreferences(application) }
    private val spaceId: String by lazy {
        SpacePathHelper.currentSpaceId(application) ?: error("Space is not selected")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            val locale = Locale.getDefault()
            val date = SimpleDateFormat("dd.MM.yyyy", locale).format(now)
            val day = SimpleDateFormat("EEEE", locale).format(now)
            _currentDateTime.value = date to day
            mainHandler.postDelayed(this, CLOCK_INTERVAL_MS)
        }
    }
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            purgeOldCompletedTasks()
            mainHandler.postDelayed(this, CLEANUP_INTERVAL_MS)
        }
    }

    init {
        loadItems()
        mainHandler.post(clockRunnable)
        mainHandler.post(cleanupRunnable)
    }

    override fun onCleared() {
        super.onCleared()
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.removeCallbacks(cleanupRunnable)
    }

    private fun loadItems() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _items.value =
                    snapshot.children.mapNotNull { SpaceCrypto.decodeSnapshot<CalendarItem>(spaceId, it) }
                        .map { item -> item.copy(createdBy = UserNameNormalizer.normalize(item.createdBy)) }
            }

            override fun onCancelled(error: DatabaseError) {
                Logger.logError(TAG, "loadItems error: $error")
            }
        })
    }

    fun addItemToDatabase(
        taskName: String,
        importanceType: Int,
        deadline: Long,
        recurrence: String
    ) {
        val itemId = database.push().key ?: return
        val item = CalendarItem(
            id = itemId,
            taskName = taskName,
            importanceType = importanceType,
            deadline = deadline,
            recurrence = recurrence,
            createdBy = UserNameNormalizer.normalize(
                prefs.getString(AppConfig.Preferences.CHARACTER_KEY, AppConfig.Characters.DEFAULT)
            ),
            timestamp = System.currentTimeMillis()
        )
        database.child(itemId).setValue(SpaceCrypto.encryptModel(spaceId, item))
    }

    fun deleteItem(itemId: String) {
        database.child(itemId).removeValue()
    }

    fun updateItem(updatedItem: CalendarItem) {
        database.child(updatedItem.id).setValue(SpaceCrypto.encryptModel(spaceId, updatedItem))
    }

    fun completeTask(item: CalendarItem) {
        if (isRecurring(item)) {
            createNextRecurringTask(item)
        } else {
            updateItem(item.copy(isStrikedThrough = true, timestamp = System.currentTimeMillis()))
        }
    }

    fun reopenTask(item: CalendarItem) {
        updateItem(item.copy(isStrikedThrough = false))
    }

    private fun isRecurring(item: CalendarItem): Boolean {
        return item.deadline > 0L &&
                RecurrenceType.fromDisplayName(
                    getApplication(),
                    item.recurrence
                ) != RecurrenceType.NEVER
    }

    private fun createNextRecurringTask(item: CalendarItem) {
        val calendar = Calendar.getInstance().apply { timeInMillis = item.deadline }
        when (RecurrenceType.fromDisplayName(getApplication(), item.recurrence)) {
            RecurrenceType.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            RecurrenceType.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            RecurrenceType.MONTHLY -> calendar.add(Calendar.MONTH, 1)
            RecurrenceType.YEARLY -> calendar.add(Calendar.YEAR, 1)
            RecurrenceType.NEVER -> return
        }

        val newId = database.push().key ?: return
        val newTask = item.copy(
            id = newId,
            deadline = calendar.timeInMillis,
            timestamp = System.currentTimeMillis(),
            isStrikedThrough = false
        )
        database.child(newId).setValue(SpaceCrypto.encryptModel(spaceId, newTask))
        deleteItem(item.id)
    }


    private fun purgeOldCompletedTasks() {
        val now = System.currentTimeMillis()
        _items.value
            ?.filter { it.isStrikedThrough && now - it.timestamp > COMPLETED_TASK_TTL_MS }
            ?.forEach { deleteItem(it.id) }
    }

    fun deleteCompletedTasks() {
        _items.value
            ?.filter { it.isStrikedThrough }
            ?.forEach { deleteItem(it.id) }
    }
}