package ru.bl3xand.pancake.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.data.model.notes.NoteColors
import ru.bl3xand.pancake.databinding.FragmentNoteOptionsBottomSheetBinding
import ru.bl3xand.pancake.utils.noteeditor.NoteColorResolver
import ru.bl3xand.pancake.utils.ui.colorResToHex
import ru.bl3xand.pancake.utils.ui.performAppHapticTap

class NoteOptionsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentNoteOptionsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val isEditMode: Boolean
        get() = arguments?.getBoolean(ARG_IS_EDIT_MODE) == true

    private var selectedColor: String = ""

    // Пары (hex → tick ImageView), инициализируются после binding
    private val colorEntries by lazy {
        val ctx = requireContext()
        listOf(
            NoteColors.DEFAULT_MARKER to binding.imgNote1,
            ctx.colorResToHex(R.color.note_color_rose) to binding.imgNote2,
            ctx.colorResToHex(R.color.note_color_orange) to binding.imgNote3,
            ctx.colorResToHex(R.color.note_color_yellow) to binding.imgNote4,
            ctx.colorResToHex(R.color.note_color_lime) to binding.imgNote5,
            ctx.colorResToHex(R.color.note_color_green) to binding.imgNote6,
            ctx.colorResToHex(R.color.note_color_sky) to binding.imgNote7,
            ctx.colorResToHex(R.color.note_color_blue) to binding.imgNote8,
            ctx.colorResToHex(R.color.note_color_purple) to binding.imgNote9,
            ctx.colorResToHex(R.color.note_color_violet) to binding.imgNote10
        )
    }

    private val colorButtons by lazy {
        val ctx = requireContext()
        listOf(
            binding.fNote1 to NoteColors.DEFAULT_MARKER,
            binding.fNote2 to ctx.colorResToHex(R.color.note_color_rose),
            binding.fNote3 to ctx.colorResToHex(R.color.note_color_orange),
            binding.fNote4 to ctx.colorResToHex(R.color.note_color_yellow),
            binding.fNote5 to ctx.colorResToHex(R.color.note_color_lime),
            binding.fNote6 to ctx.colorResToHex(R.color.note_color_green),
            binding.fNote7 to ctx.colorResToHex(R.color.note_color_sky),
            binding.fNote8 to ctx.colorResToHex(R.color.note_color_blue),
            binding.fNote9 to ctx.colorResToHex(R.color.note_color_purple),
            binding.fNote10 to ctx.colorResToHex(R.color.note_color_violet)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedColor = arguments?.getString(ARG_SELECTED_COLOR)
            .orEmpty()
            .ifBlank { NoteColors.DEFAULT_MARKER }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNoteOptionsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.layoutDeleteNote.visibility = if (isEditMode) View.VISIBLE else View.GONE
        setupColorButtons()
        binding.layoutDeleteNote.setOnClickListener {
            it.performAppHapticTap()
            showDeleteConfirmationDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupColorButtons() {
        updateSelectedColorUI()
        colorButtons.forEach { (view, hex) ->
            view.setOnClickListener { selectColor(hex) }
        }
    }

    private fun selectColor(color: String) {
        selectedColor = color
        updateSelectedColorUI()
        binding.root.performAppHapticTap()
        sendResult(ACTION_COLOR, color)
    }

    private fun updateSelectedColorUI() {
        colorEntries.forEach { (hex, tickView) ->
            val isSelected = if (NoteColorResolver.isDefault(selectedColor) && NoteColorResolver.isDefault(hex)) {
                true
            } else {
                hex == selectedColor
            }
            tickView.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_note_title)
            .setMessage(R.string.confirm_delete_note_message)
            .setNegativeButton(R.string.cancel) { _, _ ->
                binding.root.performAppHapticTap()
            }
            .setPositiveButton(R.string.delete_action) { _, _ ->
                binding.root.performAppHapticTap()
                sendResult(ACTION_DELETE_NOTE)
                dismiss()
            }
            .show()
    }

    private fun sendResult(action: String, color: String = "") {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(KEY_ACTION, action)
                putString(KEY_SELECTED_COLOR, color)
            }
        )
    }

    companion object {
        const val REQUEST_KEY = "note_options_request"
        const val KEY_ACTION = "action"
        const val KEY_SELECTED_COLOR = "selectedColor"

        const val ACTION_COLOR = "Color"
        const val ACTION_DELETE_NOTE = "DeleteNote"

        private const val ARG_IS_EDIT_MODE = "arg_is_edit_mode"
        private const val ARG_SELECTED_COLOR = "arg_selected_color"

        fun newInstance(isEditMode: Boolean, selectedColor: String) =
            NoteOptionsBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_EDIT_MODE, isEditMode)
                    putString(ARG_SELECTED_COLOR, selectedColor)
                }
            }
    }
}