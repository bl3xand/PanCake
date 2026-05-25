package ru.bl3xand.pancake.ui.viewmodel

import android.app.Application
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
import ru.bl3xand.pancake.data.model.ShoppingItem
import ru.bl3xand.pancake.utils.logs.Logger
import ru.bl3xand.pancake.utils.preferences.SpacePathHelper
import ru.bl3xand.pancake.utils.preferences.getAppPreferences
import ru.bl3xand.pancake.utils.security.SpaceCrypto
import ru.bl3xand.pancake.utils.user.UserNameNormalizer

class ShoppingFragmentViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ShoppingFragmentViewModel"
    }

    private val _items = MutableLiveData<List<ShoppingItem>>()
    val items: LiveData<List<ShoppingItem>> get() = _items

    private val database: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child(
            SpacePathHelper.node(application, AppConfig.Firebase.SHOPPING)
        )

    private val sharedPreferences by lazy { getAppPreferences(application) }
    private val spaceId: String by lazy {
        SpacePathHelper.currentSpaceId(application) ?: error("Space is not selected")
    }

    init {
        loadItems()
    }

    private fun loadItems() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _items.value = snapshot.children
                    .mapNotNull { SpaceCrypto.decodeSnapshot<ShoppingItem>(spaceId, it) }
                    .map { item -> item.copy(createdBy = UserNameNormalizer.normalize(item.createdBy)) }
                    .sortedBy { it.order }
            }

            override fun onCancelled(error: DatabaseError) {
                Logger.logError(TAG, "loadItems error: $error")
            }
        })
    }

    fun addItemToDatabase(itemName: String, itemCount: String, itemType: String) {
        val itemId = database.push().key ?: return
        val currentItems = _items.value.orEmpty()
        val maxOrder = currentItems.maxOfOrNull { it.order } ?: -1
        val item = ShoppingItem(
            id = itemId,
            name = itemName,
            count = itemCount,
            type = itemType,
            createdBy = UserNameNormalizer.normalize(
                sharedPreferences.getString(
                    AppConfig.Preferences.CHARACTER_KEY,
                    AppConfig.Characters.DEFAULT
                )
            ),
            timestamp = System.currentTimeMillis(),
            order = maxOrder + 1
        )
        database.child(itemId).setValue(SpaceCrypto.encryptModel(spaceId, item))
    }

    fun onDeleteItem(itemId: String) {
        database.child(itemId).removeValue()
    }

    fun sortItemsByType() {
        val currentItems = _items.value.orEmpty()
        val activeItems = currentItems
            .filterNot { it.isStrikedThrough }
            .sortedWith(compareBy<ShoppingItem> { getTypeOrder(it.type) }.thenBy { it.order })
        val addedItems = currentItems
            .filter { it.isStrikedThrough }
            .sortedByDescending { it.timestamp }

        val sortedItems = activeItems + addedItems
        _items.value = sortedItems

        sortedItems.forEachIndexed { index, item ->
            database.child(item.id).setValue(
                SpaceCrypto.encryptModel(spaceId, item.copy(order = index))
            )
        }
    }

    fun toggleItemStrikeThrough(item: ShoppingItem) {
        val currentItems = _items.value.orEmpty()
        val updatedItem = if (item.isStrikedThrough) {
            val lastActiveOrder = currentItems
                .filter { !it.isStrikedThrough && it.id != item.id }
                .maxOfOrNull { it.order } ?: -1
            item.copy(
                isStrikedThrough = false,
                order = lastActiveOrder + 1,
                timestamp = System.currentTimeMillis()
            )
        } else {
            val maxOrder = currentItems
                .filter { it.id != item.id }
                .maxOfOrNull { it.order } ?: -1
            item.copy(
                isStrikedThrough = true,
                order = maxOrder + 1,
                timestamp = System.currentTimeMillis()
            )
        }

        database.child(item.id).setValue(SpaceCrypto.encryptModel(spaceId, updatedItem))
        _items.value = currentItems
            .map { currentItem -> if (currentItem.id == item.id) updatedItem else currentItem }
            .sortedBy { it.order }
    }

    fun deleteAllItems() {
        _items.value?.forEach { item ->
            database.child(item.id).removeValue()
        }
        _items.value = emptyList()
    }

    fun deleteBoughtItems() {
        val boughtItems = _items.value?.filter { it.isStrikedThrough } ?: return
        boughtItems.forEach { item ->
            database.child(item.id).removeValue()
        }
        _items.value = _items.value?.filterNot { it.isStrikedThrough } ?: emptyList()
    }

    private fun getTypeOrder(type: String): Int {
        val app = getApplication<Application>()
        return when (type) {
            app.getString(R.string.food) -> 0
            app.getString(R.string.household_goods) -> 1
            app.getString(R.string.clothes) -> 2
            app.getString(R.string.home_goods) -> 3
            app.getString(R.string.tech_goods) -> 4
            app.getString(R.string.other_products) -> 5
            else -> 6
        }
    }
}