package com.example.wppsticker.ui.components

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wppsticker.R

@Composable
fun CreatePackageDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, author: String, isAnimated: Boolean, email: String, website: String, privacyPolicy: String, license: String) -> Unit,
    forceIsAnimated: Boolean? = null
) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var isAnimated by remember { mutableStateOf(forceIsAnimated ?: false) }
    
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var privacyPolicy by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    
    var showAdvanced by remember { mutableStateOf(false) }
    
    // Validation States
    var nameError by remember { mutableStateOf(false) }
    var authorError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var websiteError by remember { mutableStateOf(false) }
    var privacyError by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    fun validate(): Boolean {
        nameError = name.isBlank()
        authorError = author.isBlank()
        
        // Check optional fields only if they are not blank
        emailError = email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()
        websiteError = website.isNotBlank() && !Patterns.WEB_URL.matcher(website).matches()
        privacyError = privacyPolicy.isNotBlank() && !Patterns.WEB_URL.matcher(privacyPolicy).matches()
        
        return !nameError && !authorError && !emailError && !websiteError && !privacyError
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_package_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it 
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.pack_name_label)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) { { Text(stringResource(R.string.pkg_name_required_error)) } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Author
                OutlinedTextField(
                    value = author,
                    onValueChange = { 
                        author = it
                        authorError = false
                    },
                    label = { Text(stringResource(R.string.author_label)) },
                    singleLine = true,
                    isError = authorError,
                    supportingText = if (authorError) { { Text(stringResource(R.string.author_required_error)) } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (validate()) {
                            onCreate(name, author, isAnimated, email, website, privacyPolicy, license)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Animated Switch - DISABLED for now
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        // .clickable(enabled = forceIsAnimated == null) { isAnimated = !isAnimated } // Disable click
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.animated_pack_label), color = Color.Gray)
                        Text(
                            stringResource(R.string.coming_soon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Switch(
                        checked = false, // Force unchecked
                        onCheckedChange = { }, // Do nothing
                        enabled = false // Disable
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                // Advanced Options Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Options (Optional)", color = MaterialTheme.colorScheme.primary)
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                AnimatedVisibility(visible = showAdvanced) {
                    Column {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { 
                                email = it
                                emailError = false
                            },
                            label = { Text(stringResource(R.string.email_optional)) },
                            singleLine = true,
                            isError = emailError,
                            supportingText = if (emailError) { { Text(stringResource(R.string.invalid_email_format)) } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = website,
                            onValueChange = { 
                                website = it
                                websiteError = false
                            },
                            label = { Text(stringResource(R.string.website_optional)) },
                            singleLine = true,
                            isError = websiteError,
                            supportingText = if (websiteError) { { Text(stringResource(R.string.invalid_url_error)) } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = privacyPolicy,
                            onValueChange = { 
                                privacyPolicy = it
                                privacyError = false
                            },
                            label = { Text(stringResource(R.string.privacy_policy_optional)) },
                            singleLine = true,
                            isError = privacyError,
                            supportingText = if (privacyError) { { Text(stringResource(R.string.invalid_url_error)) } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = license,
                            onValueChange = { license = it },
                            label = { Text(stringResource(R.string.license_optional)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                if (validate()) {
                                    // Ensure isAnimated is always false
                                    onCreate(name, author, false, email, website, privacyPolicy, license)
                                }
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (validate()) {
                        // Ensure isAnimated is always false
                        onCreate(name, author, false, email, website, privacyPolicy, license)
                    }
                }
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
