package com.umit.budgettracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.CategoryType
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun addCategory(name: String, iconName: String) {
        viewModelScope.launch {
            if (hasDuplicateName(name, null)) {
                _message.emit("Bu kategori adı zaten kullanılıyor.")
                return@launch
            }
            repository.upsertCategory(
                Category(
                    id = 0,
                    name = name,
                    iconName = iconName,
                    colorValue = 0xFF9E9E9E.toInt(),
                    type = CategoryType.EXPENSE,
                    isDefault = false,
                    isActive = true,
                    sortOrder = 0
                )
            )
            _message.emit("Kategori eklendi.")
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            if (hasDuplicateName(category.name, category.id)) {
                _message.emit("Bu kategori adı zaten kullanılıyor.")
                return@launch
            }
            repository.upsertCategory(category)
            _message.emit("Kategori güncellendi.")
        }
    }

    fun toggleCategory(category: Category) {
        viewModelScope.launch {
            repository.upsertCategory(category.copy(isActive = !category.isActive))
            _message.emit(if (category.isActive) "Kategori pasifleştirildi." else "Kategori aktifleştirildi.")
        }
    }

    fun requestDelete(category: Category) {
        viewModelScope.launch {
            _message.emit(
                if (category.isDefault) {
                    "Varsayılan kategoriler silinemez. Pasifleştirebilirsiniz."
                } else {
                    "Bu kategori kullanıldığı için silinemez. Pasifleştirebilirsiniz."
                }
            )
        }
    }

    private suspend fun hasDuplicateName(name: String, currentId: Long?): Boolean {
        return repository.observeAllCategories().first().any {
            it.id != currentId && it.name.equals(name.trim(), ignoreCase = true)
        }
    }
}
