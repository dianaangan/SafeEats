package com.android.safeeats.utils

import android.app.Activity
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

fun Activity.toast(msg: String) {
    Toast.makeText(this,msg, Toast.LENGTH_LONG).show()
}

fun EditText.empty(): Boolean{
    return this.text.toString().isNullOrEmpty()
}

fun Button.setClickListener(action: () -> Unit) {
    this.setOnClickListener { action() }
}

fun TextView.clearText() {
    this.text = ""
}

fun togglePasswordVisibility(isVisible: Boolean, editText: EditText) {
    if (isVisible) {
        // Show password
        editText.transformationMethod = null
        // Optional: Change icon to "hide password"
    } else {
        // Hide password
        editText.transformationMethod = PasswordTransformationMethod.getInstance()
        // Optional: Change icon to "show password"
    }
    // Place cursor at the end of text
    editText.setSelection(editText.text.length)
}