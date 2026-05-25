package ru.bl3xand.pancake.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.databinding.ActivityMainBinding
import ru.bl3xand.pancake.ui.dialogs.Dialogs
import ru.bl3xand.pancake.ui.fragment.*
import ru.bl3xand.pancake.ui.viewmodel.CalendarFragmentViewModel
import ru.bl3xand.pancake.ui.viewmodel.MainViewModel
import ru.bl3xand.pancake.ui.viewmodel.ShoppingFragmentViewModel
import ru.bl3xand.pancake.ui.viewmodelfactory.CalendarFragmentViewModelFactory
import ru.bl3xand.pancake.ui.viewmodelfactory.ShoppingFragmentViewModelFactory
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.logs.Logger
import ru.bl3xand.pancake.utils.managers.ActivityResultManager
import ru.bl3xand.pancake.utils.permissions.PermissionsRequestManager
import ru.bl3xand.pancake.widget.QuickActionsWidgetProvider

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val THREE_FINGER_SWIPE_DOWN_THRESHOLD_PX = 120f
    }

    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()
    private val permissionResult = ActivityResultManager.permissionResultLauncher(activity = this)
    private var threeFingerStartY: Float? = null
    private var threeFingerGestureConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.fragmentContainer.setPadding(0, statusBars.top, 0, 0)
            binding.bottomNavigation.setPadding(0, 0, 0, navBars.bottom)

            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
                .build()
        }

        PermissionsRequestManager(this, permissionResult).permissionRequest()
        observeViewModel()
        setupUI(savedInstanceState)
        handleWidgetIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            openFrag(ShoppingFragment(), ShoppingFragment::class.java.simpleName)
        }

        val bottomNavigationView = binding.bottomNavigation
        bottomNavigationView.inflateMenu(R.menu.bottom_nav_menu)
        bottomNavigationView.setOnItemSelectedListener { item ->
            bottomNavigationView.performAppHapticTap()
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (item.itemId) {
                R.id.nav_shopping -> {
                    if (currentFragment !is ShoppingFragment) {
                        openFrag(ShoppingFragment(), ShoppingFragment::class.java.simpleName)
                    }
                }

                R.id.nav_calendar -> {
                    if (currentFragment !is CalendarFragment) {
                        openFrag(CalendarFragment(), CalendarFragment::class.java.simpleName)
                    }
                }

                R.id.nav_movie -> {
                    if (currentFragment !is MovieFragment) {
                        openFrag(MovieFragment(), MovieFragment::class.java.simpleName)
                    }
                }

                R.id.nav_notes -> {
                    if (currentFragment !is NotesFragment) {
                        openFrag(NotesFragment(), NotesFragment::class.java.simpleName)
                    }
                }
            }
            true
        }
    }

    private fun observeViewModel() {
        mainViewModel.characterSelected.observe(this) { isSelected ->
            if (!isSelected) {
                val intent = Intent(this, ChooseCharacterActivity::class.java)
                startActivity(intent)
                finish()
                Logger.logDebug(TAG, "Character is not selected")
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount == 3) {
                    val y1 = ev.getY(0)
                    val y2 = ev.getY(1)
                    val y3 = ev.getY(2)
                    threeFingerStartY = (y1 + y2 + y3) / 3f
                    threeFingerGestureConsumed = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount == 3 && !threeFingerGestureConsumed) {
                    val start = threeFingerStartY
                    if (start != null) {
                        val y1 = ev.getY(0)
                        val y2 = ev.getY(1)
                        val y3 = ev.getY(2)
                        val currentAvgY = (y1 + y2 + y3) / 3f
                        if (currentAvgY - start > THREE_FINGER_SWIPE_DOWN_THRESHOLD_PX) {
                            threeFingerGestureConsumed = true
                            startActivity(Intent(this, SpaceShareActivity::class.java))
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                threeFingerStartY = null
                threeFingerGestureConsumed = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun setBottomNavigationVisible(visible: Boolean) {
        binding.bottomNavigation.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun openFrag(fragment: Fragment, tag: String) =
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, fragment, tag)
            commit()
            Logger.logDebug(TAG, "Fragment $tag opened")
        }

    // --- Widget ---

    private fun handleWidgetIntent(intent: Intent?) {
        val action = intent?.getStringExtra(QuickActionsWidgetProvider.EXTRA_WIDGET_ACTION) ?: return
        intent.removeExtra(QuickActionsWidgetProvider.EXTRA_WIDGET_ACTION)

        when (action) {
            QuickActionsWidgetProvider.ACTION_SHOPPING -> {
                navigateToTab(R.id.nav_shopping)
                showShoppingAddDialog()
            }
            QuickActionsWidgetProvider.ACTION_CALENDAR -> {
                navigateToTab(R.id.nav_calendar)
                showCalendarAddDialog()
            }
            QuickActionsWidgetProvider.ACTION_MOVIE -> {
                navigateToTab(R.id.nav_movie)
                openMovieSearch()
            }
            QuickActionsWidgetProvider.ACTION_NOTE -> {
                navigateToTab(R.id.nav_notes)
                openNoteEditor()
            }
        }
    }

    private fun navigateToTab(tabId: Int) {
        binding.bottomNavigation.selectedItemId = tabId
    }

    private fun showShoppingAddDialog() {
        binding.root.post {
            val vm = ViewModelProvider(
                this, ShoppingFragmentViewModelFactory(application)
            )[ShoppingFragmentViewModel::class.java]

            Dialogs.showShoppingAddItemDialog(
                context = this,
                addItemToDatabase = { name, count, type ->
                    vm.addItemToDatabase(name, count, type)
                }
            )
        }
    }

    private fun showCalendarAddDialog() {
        binding.root.post {
            val vm = ViewModelProvider(
                this, CalendarFragmentViewModelFactory(application)
            )[CalendarFragmentViewModel::class.java]

            Dialogs.showAddCalendarItemDialog(
                context = this,
                addItemToDatabase = { name, importance, deadline, recurrence ->
                    vm.addItemToDatabase(name, importance, deadline, recurrence)
                }
            )
        }
    }

    private fun openMovieSearch() {
        binding.root.post {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (fragment is MovieFragment) {
                supportFragmentManager.beginTransaction()
                    .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.fragment_container, SearchFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun openNoteEditor() {
        binding.root.post {
            supportFragmentManager.beginTransaction()
                .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.fragment_container, NoteEditorFragment.newInstance(null))
                .addToBackStack(NoteEditorFragment::class.java.simpleName)
                .commit()
        }
    }
}