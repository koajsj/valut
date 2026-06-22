package com.offlinevault.viewmodel

import androidx.lifecycle.ViewModel
import com.offlinevault.security.GeneratorOptions
import com.offlinevault.security.PasswordGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GeneratorViewModel : ViewModel() {

    private val _options = MutableStateFlow(GeneratorOptions())
    val options: StateFlow<GeneratorOptions> = _options.asStateFlow()

    private val _password = MutableStateFlow(PasswordGenerator.generate(GeneratorOptions()))
    val password: StateFlow<String> = _password.asStateFlow()

    fun setLength(length: Int) = updateOptions { it.copy(length = length) }
    fun setUpper(value: Boolean) = updateOptions { it.copy(useUpper = value) }
    fun setLower(value: Boolean) = updateOptions { it.copy(useLower = value) }
    fun setDigits(value: Boolean) = updateOptions { it.copy(useDigits = value) }
    fun setSymbols(value: Boolean) = updateOptions { it.copy(useSymbols = value) }

    private fun updateOptions(transform: (GeneratorOptions) -> GeneratorOptions) {
        var next = transform(_options.value)
        // Never allow zero character classes.
        if (!next.useUpper && !next.useLower && !next.useDigits && !next.useSymbols) {
            next = next.copy(useLower = true)
        }
        _options.value = next
        regenerate()
    }

    fun regenerate() {
        _password.value = PasswordGenerator.generate(_options.value)
    }
}
