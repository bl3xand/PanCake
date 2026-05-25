package ru.bl3xand.pancake.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import ru.bl3xand.pancake.R
import ru.bl3xand.pancake.databinding.FragmentShoppingBinding
import ru.bl3xand.pancake.di.components.adapter.ShoppingAdapter
import ru.bl3xand.pancake.ui.dialogs.Dialogs
import ru.bl3xand.pancake.ui.viewmodel.ShoppingFragmentViewModel
import ru.bl3xand.pancake.ui.viewmodelfactory.ShoppingFragmentViewModelFactory
import ru.bl3xand.pancake.utils.ui.performAppHapticTap
import ru.bl3xand.pancake.utils.ui.applyTertiaryContainerTint
import ru.bl3xand.pancake.utils.ui.UnifiedItemDecoration

class ShoppingFragment : Fragment() {

    private var _binding: FragmentShoppingBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ShoppingFragmentViewModel
    private lateinit var adapter: ShoppingAdapter

    private var isEditMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShoppingBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(
            this,
            ShoppingFragmentViewModelFactory(requireActivity().application)
        )[ShoppingFragmentViewModel::class.java]

        setupRecyclerView()
        setupFab()
        setupButtons()
        observeViewModel()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Настройка списка покупок и отступов между карточками.
    private fun setupRecyclerView() {
        adapter = ShoppingAdapter(
            context = requireContext(),
            items = mutableListOf(),
            onDeleteItem = viewModel::onDeleteItem,
            onItemClick = viewModel::toggleItemStrikeThrough
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@ShoppingFragment.adapter
            // Единый механизм отступов для всех экранов
            addItemDecoration(UnifiedItemDecoration { position ->
                this@ShoppingFragment.adapter.isHeader(position)
            })
        }
    }

    private fun setupFab() {
        binding.fabAddItem.applyTertiaryContainerTint()
        binding.fabAddItem.setOnClickListener {
            binding.fabAddItem.performAppHapticTap()
            Dialogs.showShoppingAddItemDialog(
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
        binding.buttonSort.setOnClickListener {
            binding.buttonSort.performAppHapticTap()
            if (isEditMode) {
                Dialogs.showDeleteConfirmationDialog(
                    context = requireContext(),
                    titleRes = R.string.confirm_delete_bought_title,
                    messageRes = R.string.confirm_delete_bought_message,
                    onConfirm = {
                        viewModel.deleteBoughtItems()
                        exitEditMode()
                    }
                )
            } else {
                viewModel.sortItemsByType()
            }
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        adapter.setEditMode(isEditMode)
        binding.fabAddItem.visibility = if (isEditMode) View.GONE else View.VISIBLE
        binding.buttonEdit.text = getString(if (isEditMode) R.string.save_action else R.string.edit_action)
        updateSortButtonStyle()
    }

    private fun exitEditMode() {
        if (isEditMode) toggleEditMode()
    }

    private fun updateSortButtonStyle() {
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
        val labelRes = if (isEditMode) R.string.delete_bought_action else R.string.sort_action

        binding.buttonSort.apply {
            text = getString(labelRes)
            backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, bgAttr))
            setTextColor(MaterialColors.getColor(this, textAttr))
        }
    }


    private fun observeViewModel() {
        viewModel.items.observe(viewLifecycleOwner) { adapter.updateItems(it) }
    }
}