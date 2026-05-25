package ru.bl3xand.pancake.ui.fragment

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.databinding.FragmentCalendarBinding
import ru.bl3xand.pancake.data.model.list.CalendarListItem
import ru.bl3xand.pancake.di.components.adapter.CalendarAdapter
import ru.bl3xand.pancake.ui.dialogs.Dialogs
import ru.bl3xand.pancake.ui.viewmodel.CalendarFragmentViewModel
import ru.bl3xand.pancake.ui.viewmodelfactory.CalendarFragmentViewModelFactory
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.ui.applyTertiaryContainerTint
import ru.bl3xand.pancake.utils.ui.UnifiedItemDecoration

class CalendarFragment : Fragment() {

    companion object {
        private const val REFRESH_INTERVAL_MS = 60_000L
    }

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CalendarFragmentViewModel
    private lateinit var adapter: CalendarAdapter

    private var isEditMode = false

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        @SuppressLint("NotifyDataSetChanged")
        override fun run() {
            adapter.notifyDataSetChanged()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(
            this,
            CalendarFragmentViewModelFactory(requireActivity().application)
        )[CalendarFragmentViewModel::class.java]

        setupRecyclerView()
        setupFab()
        setupButtons()
        observeViewModel()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(refreshRunnable)
        _binding = null
    }

    // Настройка списка задач и отступов.
    private fun setupRecyclerView() {
        adapter = CalendarAdapter(
            context = requireContext(),
            items = mutableListOf(),
            onDeleteItem = viewModel::deleteItem,
            onItemClick = ::showTaskDialog
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@CalendarFragment.adapter
            // Единый механизм отступов для всех экранов
            addItemDecoration(UnifiedItemDecoration { position ->
                this@CalendarFragment.adapter.isHeader(position)
            })
        }
    }

    private fun setupFab() {
        binding.fabAddItem.applyTertiaryContainerTint()
        binding.fabAddItem.setOnClickListener {
            binding.fabAddItem.performAppHapticTap()
            Dialogs.showAddCalendarItemDialog(
                context = requireContext(),
                addItemToDatabase = viewModel::addItemToDatabase
            )
        }
    }

    private fun setupButtons() {
        binding.buttonEdit.setOnClickListener {
            binding.buttonEdit.performAppHapticTap()
            toggleEditMode()
        }
        binding.buttonCalendar.setOnClickListener {
            binding.buttonCalendar.performAppHapticTap()
            if (isEditMode) {
                Dialogs.showDeleteConfirmationDialog(
                    context = requireContext(),
                    titleRes = R.string.confirm_delete_completed_title,
                    messageRes = R.string.confirm_delete_completed_message,
                    onConfirm = {
                        viewModel.deleteCompletedTasks()
                        exitEditMode()
                    }
                )
            } else {
                openSystemCalendar()
            }
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        adapter.setEditMode(isEditMode)
        binding.fabAddItem.visibility = if (isEditMode) View.GONE else View.VISIBLE
        binding.buttonEdit.text = getString(if (isEditMode) R.string.save_action else R.string.edit_action)
        binding.dateWidget.visibility = if (isEditMode) View.GONE else View.VISIBLE
        updateCalendarButtonStyle()
    }

    private fun exitEditMode() {
        if (isEditMode) toggleEditMode()
    }

    private fun updateCalendarButtonStyle() {
        val bgAttr = if (isEditMode) {
            com.google.android.material.R.attr.colorTertiaryContainer
        } else {
            com.google.android.material.R.attr.colorPrimaryContainer
        }
        val textAttr = if (isEditMode) {
            com.google.android.material.R.attr.colorOnTertiaryContainer
        } else {
            com.google.android.material.R.attr.colorOnPrimaryContainer
        }
        val labelRes = if (isEditMode) R.string.delete_completed_action else R.string.calendar

        binding.buttonCalendar.apply {
            text = getString(labelRes)
            backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, bgAttr))
            setTextColor(MaterialColors.getColor(this, textAttr))
        }
    }

    private fun openSystemCalendar() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendQueryParameter("view", "month")
                .appendQueryParameter("beginTime", System.currentTimeMillis().toString())
                .build()
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.calendar_not_installed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun observeViewModel() {
        viewModel.items.observe(viewLifecycleOwner) { adapter.updateItems(it) }
        viewModel.currentDateTime.observe(viewLifecycleOwner) { (date, day) ->
            binding.currentDate.text = date
            binding.currentDay.text = day
        }
    }


    private fun showTaskDialog(position: Int) {
        val item = (adapter.getItem(position) as? CalendarListItem.TaskItem)?.calendarItem ?: return
        Dialogs.showTaskOptionsDialog(
            context = requireContext(),
            item = item,
            onCompleteTask = viewModel::completeTask,
            onAddToCalendar = ::addTaskToSystemCalendar,
            onEditTask = { itemToEdit ->
                Dialogs.showTaskEditDialog(
                    context = requireContext(),
                    item = itemToEdit,
                    onUpdateItem = viewModel::updateItem
                )
            },
            onReopenTask = viewModel::reopenTask
        )
    }

    private fun addTaskToSystemCalendar(taskName: String, deadline: Long, recurrence: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, taskName)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, deadline)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, deadline + 60 * 60 * 1000L)
            putExtra(CalendarContract.Events.RRULE, recurrenceToRule(recurrence))
        }
        startActivity(intent)
    }

    private fun recurrenceToRule(recurrence: String): String = when (recurrence) {
        getString(R.string.recurrence_daily) -> "FREQ=DAILY"
        getString(R.string.recurrence_weekly) -> "FREQ=WEEKLY"
        getString(R.string.recurrence_monthly) -> "FREQ=MONTHLY"
        getString(R.string.recurrence_yearly) -> "FREQ=YEARLY"
        else -> ""
    }
}