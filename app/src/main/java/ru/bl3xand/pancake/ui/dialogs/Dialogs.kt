package ru.bl3xand.pancake.ui.dialogs

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.CalendarItem
import ru.bl3xand.pancake.data.model.MovieItem
import ru.bl3xand.pancake.utils.MovieTypeHelper
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Dialogs {

    @SuppressLint("MissingInflatedId")
    fun showAddCalendarItemDialog(
        context: Context,
        addItemToDatabase: (String, Int, Long, String) -> Unit
    ) {
        val dialogView =
            LayoutInflater.from(context).inflate(R.layout.calendar_dialog_add_item, null)
        val taskNameEditText = dialogView.findViewById<EditText>(R.id.editTextTaskName)
        val taskNameLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputTaskName)
        val importanceDropdown =
            dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownImportance)
        val recurrenceDropdown =
            dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownRecurrence)
        val recurrenceLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputRecurrence)
        val noTimeCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.checkBoxNoTime)
        val dateButton = dialogView.findViewById<Button>(R.id.buttonSelectDate)
        val timeButton = dialogView.findViewById<Button>(R.id.buttonSelectTime)

        val importanceLevels = importanceOptions(context)
        setupDropdown(context, importanceDropdown, importanceLevels)

        val recurrenceOptions = recurrenceOptions(context)
        setupDropdown(context, recurrenceDropdown, recurrenceOptions)

        val calendar = Calendar.getInstance()
        updateDateButtonText(context, dateButton, calendar)
        updateTimeButtonText(context, timeButton, calendar)
        bindNoTimeToggle(noTimeCheckBox, dateButton, timeButton, recurrenceLayout)

        dateButton.setOnClickListener {
            showDatePicker(context, calendar) {
                updateDateButtonText(context, dateButton, calendar)
            }
        }

        timeButton.setOnClickListener {
            showTimePicker(context, calendar) {
                updateTimeButtonText(context, timeButton, calendar)
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_add_task_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_add_action, null)
            .setNegativeButton(R.string.dialog_cancel_action) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        configurePositiveNegativeButtons(
            dialog,
            onPositive = {
                val taskName = requireNonBlank(taskNameLayout, taskNameEditText, context)
                    ?: return@configurePositiveNegativeButtons
                val importanceType = getSelectedIndex(importanceDropdown, importanceLevels) + 1
                val deadline = if (noTimeCheckBox.isChecked) 0L else calendar.timeInMillis
                val recurrence = if (noTimeCheckBox.isChecked) {
                    context.getString(R.string.recurrence_never)
                } else {
                    recurrenceOptions[getSelectedIndex(recurrenceDropdown, recurrenceOptions)]
                }

                addItemToDatabase(taskName, importanceType, deadline, recurrence)
                dialog.dismiss()
            }
        )
        dialog.show()
    }

    fun showTaskOptionsDialog(
        context: Context,
        item: CalendarItem,
        onCompleteTask: (CalendarItem) -> Unit,
        onAddToCalendar: (String, Long, String) -> Unit,
        onEditTask: (CalendarItem) -> Unit,
        onReopenTask: (CalendarItem) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_task_options, null)
        val completeButton = dialogView.findViewById<MaterialButton>(R.id.completeButton)
        val addNotifyButton = dialogView.findViewById<MaterialButton>(R.id.addNotifyButton)
        val editButton = dialogView.findViewById<MaterialButton>(R.id.editButton)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        if (item.isStrikedThrough) {
            completeButton.setText(R.string.task_reopen)
            completeButton.setIconResource(R.drawable.reopen_icon)
            completeButton.setOnClickListener {
                performTapHaptic(completeButton)
                onReopenTask(item)
                dialog.dismiss()
            }
        } else {
            // Кнопка выполнения остается видна для всех задач (включая повторяющиеся)
            completeButton.setText(R.string.task_completed)
            completeButton.setIconResource(R.drawable.complete_icon)
            completeButton.setOnClickListener {
                performTapHaptic(completeButton)
                onCompleteTask(item)
                dialog.dismiss()
            }
        }

        addNotifyButton.setOnClickListener {
            performTapHaptic(addNotifyButton)
            onAddToCalendar(item.taskName, item.deadline, item.recurrence)
            dialog.dismiss()
        }
        editButton.setOnClickListener {
            performTapHaptic(editButton)
            onEditTask(item)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showTaskEditDialog(
        context: Context,
        item: CalendarItem,
        onUpdateItem: (CalendarItem) -> Unit
    ) {
        val dialogView =
            LayoutInflater.from(context).inflate(R.layout.calendar_dialog_add_item, null)
        val taskNameEditText = dialogView.findViewById<EditText>(R.id.editTextTaskName)
        val importanceDropdown =
            dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownImportance)
        val recurrenceDropdown =
            dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownRecurrence)
        val recurrenceLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputRecurrence)
        val noTimeCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.checkBoxNoTime)
        val dateButton = dialogView.findViewById<Button>(R.id.buttonSelectDate)
        val timeButton = dialogView.findViewById<Button>(R.id.buttonSelectTime)

        taskNameEditText.setText(item.taskName)

        val importanceLevels = importanceOptions(context)
        val safeImportanceIndex = (item.importanceType - 1).coerceIn(0, importanceLevels.lastIndex)
        setupDropdown(context, importanceDropdown, importanceLevels, safeImportanceIndex)

        val recurrenceOptions = recurrenceOptions(context)
        val safeRecurrenceIndex = recurrenceOptions.indexOf(item.recurrence).takeIf { it >= 0 } ?: 0
        setupDropdown(context, recurrenceDropdown, recurrenceOptions, safeRecurrenceIndex)

        val calendar = Calendar.getInstance().apply {
            if (item.deadline > 0L) {
                timeInMillis = item.deadline
            }
        }
        updateDateButtonText(context, dateButton, calendar)
        updateTimeButtonText(context, timeButton, calendar)
        noTimeCheckBox.isChecked = item.deadline <= 0L
        bindNoTimeToggle(noTimeCheckBox, dateButton, timeButton, recurrenceLayout)

        dateButton.setOnClickListener {
            showDatePicker(context, calendar) {
                updateDateButtonText(context, dateButton, calendar)
            }
        }

        timeButton.setOnClickListener {
            showTimePicker(context, calendar) {
                updateTimeButtonText(context, timeButton, calendar)
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_edit_task_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_save_action, null)
            .setNegativeButton(R.string.dialog_cancel_action) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        configurePositiveNegativeButtons(
            dialog,
            onPositive = {
                val updatedTaskName = taskNameEditText.text.toString().trim()
                val updatedImportance = getSelectedIndex(importanceDropdown, importanceLevels) + 1
                val updatedDeadline = if (noTimeCheckBox.isChecked) 0L else calendar.timeInMillis
                val updatedRecurrence = if (noTimeCheckBox.isChecked) {
                    context.getString(R.string.recurrence_never)
                } else {
                    recurrenceOptions[getSelectedIndex(recurrenceDropdown, recurrenceOptions)]
                }

                val updatedItem = item.copy(
                    taskName = updatedTaskName,
                    importanceType = updatedImportance,
                    deadline = updatedDeadline,
                    recurrence = updatedRecurrence
                )

                onUpdateItem(updatedItem)
                dialog.dismiss()
            }
        )
        dialog.show()
    }

    fun showShoppingAddItemDialog(
        context: Context,
        addItemToDatabase: (String, String, String) -> Unit
    ) {
        val dialogView =
            LayoutInflater.from(context).inflate(R.layout.shopping_dialog_add_item, null)
        val itemNameEditText = dialogView.findViewById<EditText>(R.id.editTextItemName)
        val itemNameLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputItemName)
        val itemCountEditText = dialogView.findViewById<EditText>(R.id.editTextItemCount)
        val itemTypeDropdown =
            dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownItemType)

        val itemTypes = shoppingTypeOptions(context)
        setupDropdown(context, itemTypeDropdown, itemTypes)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_add_item_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_add_action, null)
            .setNegativeButton(R.string.dialog_cancel_action) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        configurePositiveNegativeButtons(
            dialog,
            onPositive = {
                val itemName = requireNonBlank(itemNameLayout, itemNameEditText, context)
                    ?: return@configurePositiveNegativeButtons

                val itemCountText = itemCountEditText.text.toString().trim()
                val itemCount = if (itemCountText.isBlank()) "1 шт." else "$itemCountText шт."

                val itemType = itemTypes[getSelectedIndex(itemTypeDropdown, itemTypes)]

                addItemToDatabase(itemName, itemCount, itemType)
                dialog.dismiss()
            }
        )
        dialog.show()
    }

    fun showSeasonEpisodeDialog(context: Context, movie: MovieItem, onSave: (Int, Int) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_season_episode, null)
        val seasonLayout = view.findViewById<TextInputLayout>(R.id.textInputSeason)
        val episodeLayout = view.findViewById<TextInputLayout>(R.id.textInputEpisode)
        val seasonEditText = view.findViewById<EditText>(R.id.editTextSeason)
        val episodeEditText = view.findViewById<EditText>(R.id.editTextEpisode)

        if (movie.season > 0) seasonEditText.setText(movie.season.toString())
        if (movie.episode > 0) episodeEditText.setText(movie.episode.toString())

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_edit_season_episode_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_save_action, null)
            .setNegativeButton(R.string.dialog_cancel_action, null)
            .create()

        configurePositiveNegativeButtons(
            dialog,
            onPositive = {
                seasonLayout.error = null
                episodeLayout.error = null

                val season = seasonEditText.text.toString().toIntOrNull()
                val episode = episodeEditText.text.toString().toIntOrNull()
                when {
                    season == null || season <= 0 -> {
                        seasonLayout.error = context.getString(R.string.error_enter_season)
                    }
                    episode == null || episode <= 0 -> {
                        episodeLayout.error = context.getString(R.string.error_enter_episode)
                    }
                    else -> {
                        onSave(season, episode)
                        dialog.dismiss()
                    }
                }
            }
        )
        dialog.show()
    }

    @SuppressLint("MissingInflatedId")
    fun showAddCustomMovieDialog(
        context: Context,
        onAddMovie: (String, String) -> Unit
    ) {
        val dialogView =
            LayoutInflater.from(context).inflate(R.layout.dialog_add_custom_movie, null)
        val titleEditText = dialogView.findViewById<EditText>(R.id.editTextMovieTitle)
        val titleLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputTitle)
        val typeDropdown =
            dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownMovieType)

        val movieTypes = listOf(
            context.getString(R.string.movie_type_movie),
            context.getString(R.string.movie_type_series)
        )
        setupDropdown(context, typeDropdown, movieTypes)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_add_custom_movie_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_add_action, null)
            .setNegativeButton(R.string.dialog_cancel_action) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        configurePositiveNegativeButtons(
            dialog,
            onPositive = {
                val title = requireNonBlank(titleLayout, titleEditText, context)
                    ?: return@configurePositiveNegativeButtons
                val type = if (getSelectedIndex(typeDropdown, movieTypes) == 0) {
                    MovieTypeHelper.TYPE_MOVIE
                } else {
                    MovieTypeHelper.TYPE_SERIES
                }

                onAddMovie(title, type)
                dialog.dismiss()
            }
        )
        dialog.show()
    }

    fun showMovieStatusDialog(
        context: Context,
        title: String,
        onStatusSelected: (String) -> Unit
    ) {
        val dialogView =
            LayoutInflater.from(context).inflate(R.layout.dialog_movie_add_status, null)
        val btnWatching = dialogView.findViewById<MaterialButton>(R.id.buttonWatching)
        val btnPlanned = dialogView.findViewById<MaterialButton>(R.id.buttonPlanned)
        val btnWatched = dialogView.findViewById<MaterialButton>(R.id.buttonWatched)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.buttonCancel)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.choose_movie_status, title))
            .setView(dialogView)
            .create()

        btnWatching.setOnClickListener {
            performTapHaptic(btnWatching)
            onStatusSelected(context.getString(R.string.movie_status_watching))
            dialog.dismiss()
        }
        btnPlanned.setOnClickListener {
            performTapHaptic(btnPlanned)
            onStatusSelected(context.getString(R.string.movie_status_planned))
            dialog.dismiss()
        }
        btnWatched.setOnClickListener {
            performTapHaptic(btnWatched)
            onStatusSelected(context.getString(R.string.movie_status_watched))
            dialog.dismiss()
        }
        btnCancel.setOnClickListener {
            performTapHaptic(btnCancel)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showMovieDetailsDialog(
        context: Context,
        movie: MovieItem,
        currentStatus: String,
        onMovieToWatching: () -> Unit,
        onMovieToPlan: () -> Unit,
        onUpdateEpisode: (Int, Int) -> Unit,
        onMovieToWatched: () -> Unit,
        onDelete: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_movie_options, null)
        val btnWatching = dialogView.findViewById<MaterialButton>(R.id.buttonWatching)
        val btnPlan = dialogView.findViewById<MaterialButton>(R.id.buttonPlan)
        val btnEpisode = dialogView.findViewById<MaterialButton>(R.id.buttonEpisode)
        val btnWatched = dialogView.findViewById<MaterialButton>(R.id.buttonWatched)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.buttonDelete)

        // Скрыть кнопку текущего статуса
        if (currentStatus == context.getString(R.string.movie_status_watching)) btnWatching.visibility =
            View.GONE
        if (currentStatus == context.getString(R.string.movie_status_planned)) btnPlan.visibility =
            View.GONE

        // Для фильмов скрываем только кнопку сезона/серии
        if (movie.type != MovieTypeHelper.TYPE_SERIES) {
            btnEpisode.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(movie.title)
            .setView(dialogView)
            .create()

        btnWatching.setOnClickListener {
            performTapHaptic(btnWatching)
            onMovieToWatching()
            dialog.dismiss()
        }
        btnPlan.setOnClickListener {
            performTapHaptic(btnPlan)
            onMovieToPlan()
            dialog.dismiss()
        }
        btnEpisode.setOnClickListener {
            performTapHaptic(btnEpisode)
            dialog.dismiss()
            // Только обновляем сезон/серию — статус не меняем
            showSeasonEpisodeDialog(context, movie) { season, episode ->
                onUpdateEpisode(
                    season,
                    episode
                )
            }
        }
        btnWatched.setOnClickListener {
            performTapHaptic(btnWatched)
            onMovieToWatched()
            dialog.dismiss()
        }
        btnDelete.setOnClickListener {
            performTapHaptic(btnDelete)
            onDelete()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showWatchedMovieOptionsDialog(
        context: Context,
        movie: MovieItem,
        onMovieToWatching: () -> Unit,
        onMovieToPlan: () -> Unit,
        onDelete: () -> Unit
    ) {
        val dialogView =
            LayoutInflater.from(context).inflate(R.layout.dialog_movie_watched_options, null)
        val btnWatching = dialogView.findViewById<MaterialButton>(R.id.buttonWatching)
        val btnPlan = dialogView.findViewById<MaterialButton>(R.id.buttonPlan)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.buttonDelete)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(movie.title)
            .setView(dialogView)
            .create()

        btnWatching.setOnClickListener {
            performTapHaptic(btnWatching)
            onMovieToWatching()
            dialog.dismiss()
        }
        btnPlan.setOnClickListener {
            performTapHaptic(btnPlan)
            onMovieToPlan()
            dialog.dismiss()
        }
        btnDelete.setOnClickListener {
            performTapHaptic(btnDelete)
            onDelete()
            dialog.dismiss()
        }

        dialog.show()
    }


    fun showDeleteConfirmationDialog(
        context: Context,
        titleRes: Int,
        messageRes: Int,
        onConfirm: () -> Unit
    ) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(R.string.delete_action, null)
            .setNegativeButton(R.string.dialog_cancel_action, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            applyNegativeActionStyle(dialog)
            applyFlatActionStyle(positiveButton)

            positiveButton.setOnClickListener {
                performTapHaptic(positiveButton)
                onConfirm()
                dialog.dismiss()
            }
            negativeButton.setOnClickListener {
                performTapHaptic(negativeButton)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDatePicker(context: Context, calendar: Calendar, onSelected: () -> Unit = {}) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    onSelected()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
            return
        }

        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(calendar.timeInMillis)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            selection ?: return@addOnPositiveButtonClickListener
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            calendar.timeInMillis = selection
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            onSelected()
        }

        picker.show(activity.supportFragmentManager, "material_date_picker")
    }

    private fun showTimePicker(context: Context, calendar: Calendar, onSelected: () -> Unit = {}) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            TimePickerDialog(context, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                onSelected()
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            return
        }

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .build()

        picker.addOnPositiveButtonClickListener {
            calendar.set(Calendar.HOUR_OF_DAY, picker.hour)
            calendar.set(Calendar.MINUTE, picker.minute)
            onSelected()
        }

        picker.show(activity.supportFragmentManager, "material_time_picker")
    }

    private fun setupDropdown(
        context: Context,
        dropdown: MaterialAutoCompleteTextView,
        options: List<String>,
        selectedIndex: Int = 0
    ) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, options)
        dropdown.setAdapter(adapter)

        val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)
        dropdown.setText(options[safeIndex], false)
    }

    private fun getSelectedIndex(
        dropdown: MaterialAutoCompleteTextView,
        options: List<String>
    ): Int {
        val selected = dropdown.text?.toString().orEmpty()
        return options.indexOf(selected).takeIf { it >= 0 } ?: 0
    }

    private fun updateDateButtonText(context: Context, button: Button, calendar: Calendar) {
        val value = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(calendar.time)
        button.text = context.getString(R.string.selected_date_format, value)
    }

    private fun updateTimeButtonText(context: Context, button: Button, calendar: Calendar) {
        val value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
        button.text = context.getString(R.string.selected_time_format, value)
    }

    private fun bindNoTimeToggle(
        checkBox: MaterialCheckBox,
        dateButton: Button,
        timeButton: Button,
        recurrenceLayout: TextInputLayout
    ) {
        val updateVisibility: (Boolean) -> Unit = { noTime ->
            val visibility = if (noTime) View.GONE else View.VISIBLE
            dateButton.visibility = visibility
            timeButton.visibility = visibility
            recurrenceLayout.visibility = visibility
        }

        updateVisibility(checkBox.isChecked)
        checkBox.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            updateVisibility(isChecked)
        }
    }

    private fun applyNegativeActionStyle(dialog: AlertDialog) {
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE) ?: return
        val textColor = MaterialColors.getColor(
            negativeButton,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        negativeButton.setBackgroundColor(Color.TRANSPARENT)
        negativeButton.setTextColor(textColor)
    }

    private fun applyDestructiveActionStyle(button: android.widget.Button?) {
        button ?: return
        val bgColor = MaterialColors.getColor(
            button,
            com.google.android.material.R.attr.colorErrorContainer
        )
        val textColor = MaterialColors.getColor(
            button,
            com.google.android.material.R.attr.colorOnErrorContainer
        )
        button.setBackgroundColor(bgColor)
        button.setTextColor(textColor)
    }

    private fun applyFlatActionStyle(button: android.widget.Button?) {
        button ?: return
        val textColor = MaterialColors.getColor(
            button,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        button.setBackgroundColor(Color.TRANSPARENT)
        button.setTextColor(textColor)
    }

    private fun performTapHaptic(view: View) {
        view.performAppHapticTap()
    }

    private fun configurePositiveNegativeButtons(
        dialog: AlertDialog,
        onPositive: () -> Unit
    ) {
        dialog.setOnShowListener {
            applyNegativeActionStyle(dialog)
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setOnClickListener {
                performTapHaptic(positiveButton)
                onPositive()
            }
            negativeButton.setOnClickListener {
                performTapHaptic(negativeButton)
                dialog.dismiss()
            }
        }
    }

    private fun requireNonBlank(
        inputLayout: TextInputLayout,
        editText: EditText,
        context: Context
    ): String? {
        val value = editText.text.toString().trim()
        if (value.isBlank()) {
            inputLayout.error = context.getString(R.string.error_required_name)
            return null
        }
        inputLayout.error = null
        return value
    }

    // Единые списки опций для форм задач/покупок.
    private fun importanceOptions(context: Context): List<String> = listOf(
        context.getString(R.string.low_importance),
        context.getString(R.string.mid_importance),
        context.getString(R.string.high_importance)
    )

    private fun recurrenceOptions(context: Context): List<String> = listOf(
        context.getString(R.string.recurrence_never),
        context.getString(R.string.recurrence_daily),
        context.getString(R.string.recurrence_weekly),
        context.getString(R.string.recurrence_monthly),
        context.getString(R.string.recurrence_yearly)
    )

    private fun shoppingTypeOptions(context: Context): List<String> = listOf(
        context.getString(R.string.food),
        context.getString(R.string.household_goods),
        context.getString(R.string.clothes),
        context.getString(R.string.home_goods),
        context.getString(R.string.tech_goods),
        context.getString(R.string.other_products)
    )

    fun showExitSpaceConfirmationDialog(
        context: Context,
        onConfirm: () -> Unit
    ) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.confirm_exit_space_title)
            .setMessage(R.string.confirm_exit_space_message)
            .setPositiveButton(R.string.exit, null)
            .setNegativeButton(R.string.dialog_cancel_action, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            applyNegativeActionStyle(dialog)
            applyFlatActionStyle(positiveButton)

            positiveButton.setOnClickListener {
                performTapHaptic(positiveButton)
                onConfirm()
                dialog.dismiss()
            }
            negativeButton.setOnClickListener {
                performTapHaptic(negativeButton)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun showAddMarkdownLinkDialog(
        context: Context,
        onLinkReady: (markdownLink: String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_markdown_link, null)
        val titleLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLinkTitle)
        val urlLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLinkUrl)
        val titleInput = dialogView.findViewById<EditText>(R.id.inputLinkTitle)
        val urlInput = dialogView.findViewById<EditText>(R.id.inputLinkUrl)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.note_add_link_action)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_add_action, null)
            .setNegativeButton(R.string.dialog_cancel_action, null)
            .create()

        configurePositiveNegativeButtons(
            dialog,
            onPositive = {
                val rawUrl = urlInput.text?.toString().orEmpty().trim()
                val rawTitle = titleInput.text?.toString().orEmpty().trim()

                titleLayout.error = null
                urlLayout.error = null

                if (rawUrl.isBlank()) {
                    urlLayout.error = context.getString(R.string.error_web_link_required)
                    return@configurePositiveNegativeButtons
                }

                if (!android.util.Patterns.WEB_URL.matcher(rawUrl).matches()) {
                    urlLayout.error = context.getString(R.string.error_web_link_invalid)
                    return@configurePositiveNegativeButtons
                }

                val title = rawTitle.ifBlank { rawUrl }
                onLinkReady("[$title]($rawUrl)")
                dialog.dismiss()
            }
        )
        dialog.show()
    }
}