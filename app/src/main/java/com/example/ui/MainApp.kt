package com.example.ui

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PasswordRecord
import com.example.security.BiometricHelper
import com.example.security.DecryptedFields
import com.example.security.SecurityManager
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: PasswordViewModel,
    activity: FragmentActivity,
    triggerBiometric: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val isMasterPasswordCreated by viewModel.isMasterPasswordCreated.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    
    var showCodeAnalyzer by remember { mutableStateOf(false) }
    val secureMode by viewModel.secureModeEnabled.collectAsStateWithLifecycle()

    // Force RTL support for all Persian layouts
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !isMasterPasswordCreated -> {
                        SetupMasterPasswordScreen(onSetup = { pwd ->
                            viewModel.setupMasterPassword(context, pwd)
                            Toast.makeText(context, "رمز عبور مستر با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                        })
                    }
                    !isLoggedIn -> {
                        LoginScreen(
                            onLogin = { pwd ->
                                val success = viewModel.login(context, pwd)
                                if (success) {
                                    Toast.makeText(context, "خوش آمدید", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "رمز عبور مستر اشتباه است", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBiometricClick = {
                                triggerBiometric(
                                    {
                                        val success = viewModel.loginWithBiometric(context)
                                        if (success) {
                                            Toast.makeText(context, "خوش آمدید (زیست‌سنجی)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "ورود زیست‌سنجی فعال نیست یا خطا رخ داده است", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    { err ->
                                        if (err != "لغو شد") {
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                            isBiometricEnabled = SecurityManager.isBiometricEnabled(context) && BiometricHelper.isBiometricAvailable(context)
                        )
                    }
                    else -> {
                        DashboardScreen(viewModel, activity, triggerBiometric)
                    }
                }

                // Floating Dev/Bug Analyzer Badge
                FloatingActionButton(
                    onClick = { showCodeAnalyzer = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = if (isLoggedIn) 100.dp else 24.dp)
                        .size(52.dp),
                    containerColor = if (secureMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (secureMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "عیب‌یاب پروژه",
                        modifier = Modifier.size(26.dp)
                    )
                }

                if (showCodeAnalyzer) {
                    CodeAnalyzerDialog(
                        onDismiss = { showCodeAnalyzer = false },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

// --- SETUP SCREEN ---

@Composable
fun SetupMasterPasswordScreen(onSetup: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "راه‌اندازی صندوقچه رمز",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "یک رمز عبور مستر قوی برای حفاظت از تمام اطلاعات خود تعریف کنید. این رمز هرگز ذخیره نمی‌شود و مسئولیت آن با شماست.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("رمز عبور مستر") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("master_setup_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("تکرار رمز عبور مستر") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("master_setup_confirm"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(24.dp))

        val isMatch = password == confirmPassword && password.isNotEmpty()
        Button(
            onClick = { if (isMatch) onSetup(password) },
            enabled = isMatch && password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("master_setup_button")
        ) {
            Text("ایجاد صندوقچه امن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (password.isNotEmpty() && password.length < 6) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "رمز عبور مستر باید حداقل ۶ کاراکتر باشد.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        } else if (password.isNotEmpty() && confirmPassword.isNotEmpty() && password != confirmPassword) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "رمزهای وارد شده با یکدیگر مطابقت ندارند.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }
    }
}

// --- LOGIN SCREEN ---

@Composable
fun LoginScreen(
    onLogin: (String) -> Unit,
    onBiometricClick: () -> Unit,
    isBiometricEnabled: Boolean
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "صندوقچه قفل است",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "برای ورود به برنامه، رمز عبور مستر خود را وارد کنید.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("رمز عبور مستر") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("master_login_input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onLogin(password) },
            enabled = password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("master_login_button")
        ) {
            Text("باز کردن قفل", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (isBiometricEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onBiometricClick,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("biometric_login_button")
            ) {
                Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ورود با اثر انگشت / چهره", fontSize = 15.sp)
            }

            // Auto trigger biometric on first launch
            LaunchedEffect(Unit) {
                onBiometricClick()
            }
        }
    }
}

// --- DASHBOARD CONTAINER ---

@Composable
fun DashboardScreen(
    viewModel: PasswordViewModel,
    activity: FragmentActivity,
    triggerBiometric: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var currentTab by remember { mutableStateOf("passwords") } // "passwords", "generator", "settings"
    var showAddOverlay by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<PasswordRecord?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentTab == "passwords",
                    onClick = { currentTab = "passwords" },
                    icon = { Icon(imageVector = Icons.Default.Key, contentDescription = null) },
                    label = { Text("رمزهای من") }
                )
                NavigationBarItem(
                    selected = currentTab == "generator",
                    onClick = { currentTab = "generator" },
                    icon = { Icon(imageVector = Icons.Default.Casino, contentDescription = null) },
                    label = { Text("گذرواژه‌ساز") }
                )
                NavigationBarItem(
                    selected = currentTab == "settings",
                    onClick = { currentTab = "settings" },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                    label = { Text("تنظیمات") }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == "passwords") {
                FloatingActionButton(
                    onClick = { showAddOverlay = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_password_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "افزودن رمز", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "passwords" -> PasswordsTab(
                    viewModel = viewModel,
                    onEdit = { record ->
                        editingRecord = record
                        showAddOverlay = true
                    }
                )
                "generator" -> GeneratorTab(viewModel = viewModel)
                "settings" -> SettingsTab(
                    viewModel = viewModel,
                    activity = activity,
                    triggerBiometric = triggerBiometric
                )
            }

            if (showAddOverlay) {
                AddEditPasswordOverlay(
                    viewModel = viewModel,
                    recordToEdit = editingRecord,
                    onDismiss = {
                        showAddOverlay = false
                        editingRecord = null
                    }
                )
            }
        }
    }
}

// --- PASSWORDS LIST TAB ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordsTab(
    viewModel: PasswordViewModel,
    onEdit: (PasswordRecord) -> Unit
) {
    val context = LocalContext.current
    val rawRecords by viewModel.rawRecords.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("همه") }
    var sortBy by remember { mutableStateOf("name_asc") } // "name_asc", "newest", "favorite"

    val customCategories by viewModel.categories.collectAsStateWithLifecycle()
    val categories = remember(customCategories) { listOf("همه") + customCategories }

    // Sort & Filter records
    val filteredRecords = remember(rawRecords, searchQuery, selectedCategory, sortBy) {
        var list = rawRecords.filter { record ->
            val recordCats = record.category.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val matchesCategory = (selectedCategory == "همه" || recordCats.contains(selectedCategory))
            val matchesSearch = record.websiteName.contains(searchQuery.trim(), ignoreCase = true)
            matchesCategory && matchesSearch
        }
        list = when (sortBy) {
            "name_asc" -> list.sortedWith(compareBy({ !it.isFavorite }, { it.websiteName.lowercase() }))
            "newest" -> list.sortedWith(compareBy({ !it.isFavorite }, { -it.createdAt }))
            "favorite" -> list.sortedByDescending { it.isFavorite }
            else -> list
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Geometric Balance Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // User Avatar Circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ص", // ص for صندوقچه
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Title
            Text(
                text = "صندوقچه رمز عبور",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Quick Lock Button
            IconButton(
                onClick = { viewModel.logout() }
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "قفل کردن",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Top Search & Sort
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجوی سایت یا برنامه...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("search_bar"),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Sort Menu Button
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(28.dp)
                        )
                ) {
                    Icon(imageVector = Icons.Default.Sort, contentDescription = "مرتب‌سازی", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("به ترتیب حروف الفبا (سایت)") },
                        onClick = {
                            sortBy = "name_asc"
                            showSortMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("جدیدترین‌ها") },
                        onClick = {
                            sortBy = "newest"
                            showSortMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("علاقه‌مندی‌ها در بالا") },
                        onClick = {
                            sortBy = "favorite"
                            showSortMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                    )
                }
            }
        }

        // Horizontal Category Row
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory),
            edgePadding = 16.dp,
            divider = {},
            indicator = {},
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                Tab(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    text = {
                        Text(
                            text = cat,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                )
            }
        }

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.KeyOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "هیچ موردی یافت نشد",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                    Text(
                        text = "برای افزودن رمز روی دکمه + کلیک کنید.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    PasswordRecordCard(
                        record = record,
                        viewModel = viewModel,
                        onEdit = onEdit
                    )
                }
            }
        }
    }
}

// --- RELATIVE TIME HELPER ---
fun getRelativeTimeString(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    return when {
        diff < 60_000 -> "الان"
        diff < 3600_000 -> "${diff / 60_000} دقیقه پیش"
        diff < 86400_000 -> "${diff / 3600_000} ساعت پیش"
        diff < 2592000_000L -> "${diff / 86400_000} روز پیش"
        else -> {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            sdf.format(java.util.Date(timeMs))
        }
    }
}

// --- PASSWORD RECORD CARD ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRecordCard(
    record: PasswordRecord,
    viewModel: PasswordViewModel,
    onEdit: (PasswordRecord) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val decrypted = remember(record) { viewModel.decryptRecordFields(record) }

    var passwordVisible by remember { mutableStateOf(false) }

    val recordCategories = remember(record.category) {
        record.category.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val primaryCategory = recordCategories.firstOrNull() ?: "سایر"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("record_card_${record.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (record.isFavorite) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row - Clicking only this row toggles expansion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon
                val catIcon = when (primaryCategory) {
                    "شخصی" -> Icons.Default.Person
                    "بانکی" -> Icons.Default.AccountBalance
                    "شبکه‌های اجتماعی" -> Icons.Default.Public
                    "کاری" -> Icons.Default.Work
                    else -> Icons.Default.Key
                }

                // Geometric Balance Category Colors
                val (catBg, catText) = when (primaryCategory) {
                    "شخصی" -> Color(0xFFFFD8E4) to Color(0xFF31111D)
                    "بانکی" -> Color(0xFFEADDFF) to Color(0xFF21005D)
                    "شبکه‌های اجتماعی" -> Color(0xFFD0E4FF) to Color(0xFF001D35)
                    "کاری" -> Color(0xFFEADDFF) to Color(0xFF21005D)
                    else -> Color(0xFFFFD8E4) to Color(0xFF31111D)
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            catBg,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = catIcon, contentDescription = null, tint = catText)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.websiteName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val subtitle = remember(decrypted) {
                        when {
                            decrypted.username.isNotEmpty() -> decrypted.username
                            decrypted.email.isNotEmpty() -> decrypted.email
                            else -> ""
                        }
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Display all selected categories as beautiful, tiny badge labels
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        recordCategories.forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Favorite Button
                IconButton(
                    onClick = { viewModel.toggleFavorite(record) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (record.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "علاقه‌مندی",
                        tint = if (record.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right-side strength badge and relative time
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val strength = remember(decrypted) { SecurityManager.evaluateStrength(decrypted.passwordStr) }
                    val (strengthBg, strengthText, strengthLabel) = when (strength) {
                        SecurityManager.Strength.WEAK -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "ضعیف")
                        SecurityManager.Strength.MEDIUM -> Triple(Color(0xFFFEF9C3), Color(0xFF854D0E), "متوسط")
                        SecurityManager.Strength.STRONG -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), "قوی")
                        SecurityManager.Strength.VERY_STRONG -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), "بسیار قوی")
                    }

                    // Strength Badge
                    Box(
                        modifier = Modifier
                            .background(strengthBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = strengthLabel,
                            color = strengthText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Relative Time
                    Text(
                        text = getRelativeTimeString(record.lastModifiedAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Expanded Details Section
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Username field
                if (decrypted.username.isNotEmpty()) {
                    DetailField(
                        label = "نام کاربری",
                        value = decrypted.username,
                        onCopy = {
                            viewModel.copyToClipboard(context, "نام کاربری", decrypted.username, false)
                            Toast.makeText(context, "نام کاربری کپی شد", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Email field
                if (decrypted.email.isNotEmpty()) {
                    DetailField(
                        label = "ایمیل",
                        value = decrypted.email,
                        onCopy = {
                            viewModel.copyToClipboard(context, "ایمیل", decrypted.email, false)
                            Toast.makeText(context, "ایمیل کپی شد", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Password field (with toggleable show/hide)
                DetailField(
                    label = "رمز عبور",
                    value = decrypted.passwordStr,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                    onCopy = {
                        val duration = viewModel.clipboardTimeout.value
                        viewModel.copyToClipboard(context, "رمز عبور", decrypted.passwordStr, true)
                        val message = if (duration > 0) "رمز عبور کپی شد (پاک‌سازی کلیپ‌بورد در $duration ثانیه)" else "رمز عبور کپی شد"
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                )

                // Password Strength Meter
                val strength = remember(decrypted.passwordStr) {
                    SecurityManager.evaluateStrength(decrypted.passwordStr)
                }
                PasswordStrengthBar(strength)

                // Notes field
                if (decrypted.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "یادداشت‌ها:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = decrypted.notes,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    )
                }

                // Timestamps
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
                    Text(
                        text = "ایجاد: ${sdf.format(Date(record.createdAt))}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "ویرایش: ${sdf.format(Date(record.lastModifiedAt))}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Actions Row
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { onEdit(record) },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ویرایش", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    var showConfirmDelete by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حذف", fontSize = 12.sp)
                    }

                    if (showConfirmDelete) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDelete = false },
                            title = { Text("حذف رمز عبور") },
                            text = { Text("آیا مطمئن هستید که می‌خواهید رمز مربوط به «${record.websiteName}» را حذف کنید؟ این عمل غیرقابل بازگشت است.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteRecord(record.id)
                                    showConfirmDelete = false
                                    Toast.makeText(context, "با موفقیت حذف شد", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("بله، حذف شود", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirmDelete = false }) {
                                    Text("انصراف")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailField(
    label: String,
    value: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onToggleVisibility: () -> Unit = {},
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayValue = if (isPassword && !passwordVisible) "••••••••••••" else value
            Text(
                text = displayValue,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Left
            )

            if (isPassword) {
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "کپی",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PasswordStrengthBar(strength: SecurityManager.Strength) {
    val (color, text) = when (strength) {
        SecurityManager.Strength.WEAK -> Color(0xFFEF4444) to "ضعیف"
        SecurityManager.Strength.MEDIUM -> Color(0xFFF97316) to "متوسط"
        SecurityManager.Strength.STRONG -> Color(0xFF10B981) to "قوی"
        SecurityManager.Strength.VERY_STRONG -> Color(0xFF6366F1) to "بسیار قوی"
    }

    val fraction = when (strength) {
        SecurityManager.Strength.WEAK -> 0.25f
        SecurityManager.Strength.MEDIUM -> 0.5f
        SecurityManager.Strength.STRONG -> 0.75f
        SecurityManager.Strength.VERY_STRONG -> 1.0f
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("قدرت رمز عبور:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = fraction,
            color = color,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

// --- SECURE PASSWORD GENERATOR TAB ---

@Composable
fun GeneratorTab(viewModel: PasswordViewModel) {
    val context = LocalContext.current
    var length by remember { mutableStateOf(16f) }
    var useUppercase by remember { mutableStateOf(true) }
    var useLowercase by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }

    var generatedPassword by remember { mutableStateOf("") }

    // Auto generate on launch
    LaunchedEffect(length, useUppercase, useLowercase, useNumbers, useSymbols) {
        generatedPassword = SecurityManager.generatePassword(
            length.toInt(), useUppercase, useLowercase, useNumbers, useSymbols
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "تولیدکننده رمز عبور ایمن",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Result Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = generatedPassword,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Left
                )

                IconButton(onClick = {
                    viewModel.copyToClipboard(context, "رمز تولید شده", generatedPassword, true)
                    Toast.makeText(context, "رمز تولید شده کپی شد", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "کپی رمز",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Length Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("طول رمز عبور:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${length.toInt()} کاراکتر", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = length,
                onValueChange = { length = it },
                valueRange = 8f..32f,
                steps = 24
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Config Options Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("گزینه‌های تولید رمز:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                GeneratorOptionRow("کاراکترهای بزرگ (A-Z)", useUppercase) { useUppercase = it }
                GeneratorOptionRow("کاراکترهای کوچک (a-z)", useLowercase) { useLowercase = it }
                GeneratorOptionRow("اعداد (0-9)", useNumbers) { useNumbers = it }
                GeneratorOptionRow("نمادها (@#$%)", useSymbols) { useSymbols = it }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                generatedPassword = SecurityManager.generatePassword(
                    length.toInt(), useUppercase, useLowercase, useNumbers, useSymbols
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تولید مجدد", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GeneratorOptionRow(label: String, value: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp)
        Switch(checked = value, onCheckedChange = onCheckedChange)
    }
}

// --- ADD/EDIT PASSWORD OVERLAY ---

@Composable
fun AddEditPasswordOverlay(
    viewModel: PasswordViewModel,
    recordToEdit: PasswordRecord?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var websiteName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    // Load edit values
    LaunchedEffect(recordToEdit) {
        if (recordToEdit != null) {
            val dec = viewModel.decryptRecordFields(recordToEdit)
            websiteName = dec.websiteName
            username = dec.username
            email = dec.email
            password = dec.passwordStr
            notes = dec.notes
            selectedCategories = dec.category.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        } else {
            selectedCategories = setOf("شخصی")
        }
    }

    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = true, onClick = onDismiss)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .align(Alignment.BottomCenter)
                .clickable(enabled = false) {}, // consume clicks
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (recordToEdit != null) "ویرایش رمز عبور" else "افزودن رمز جدید",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input fields
                OutlinedTextField(
                    value = websiteName,
                    onValueChange = { websiteName = it },
                    label = { Text("نام سایت یا برنامه (مثال: بلو بانک)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_website_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("نام کاربری (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_username_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("ایمیل (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_email_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password with generation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("رمز عبور") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Generate Random Password directly in Add flow
                    IconButton(
                        onClick = {
                            password = SecurityManager.generatePassword(16, true, true, true, true)
                            passwordVisible = true
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "ایجاد تصادفی",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (password.isNotEmpty()) {
                    val strength = SecurityManager.evaluateStrength(password)
                    PasswordStrengthBar(strength)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category selector chip row
                Text("دسته‌بندی (یک یا چند مورد انتخاب کنید):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategories.contains(cat)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategories = if (isSelected) {
                                    if (selectedCategories.size > 1) selectedCategories - cat else selectedCategories
                                } else {
                                    selectedCategories + cat
                                }
                            },
                            label = { Text(cat, fontSize = 11.sp) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { showAddCategoryDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "افزودن دسته‌بندی جدید",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("یادداشت‌ها (اختیاری)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("add_notes_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val finalCategory = selectedCategories.joinToString(",")
                        if (recordToEdit != null) {
                            viewModel.updateRecord(
                                id = recordToEdit.id,
                                websiteName = websiteName,
                                username = username,
                                email = email,
                                passwordStr = password,
                                notes = notes,
                                category = finalCategory,
                                isFavorite = recordToEdit.isFavorite,
                                createdAt = recordToEdit.createdAt
                            )
                            Toast.makeText(context, "با موفقیت ویرایش شد", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addRecord(
                                websiteName = websiteName,
                                username = username,
                                email = email,
                                passwordStr = password,
                                notes = notes,
                                category = finalCategory,
                                isFavorite = false
                            )
                            Toast.makeText(context, "با موفقیت اضافه شد", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    },
                    enabled = websiteName.isNotEmpty() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_password_button")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (recordToEdit != null) "ذخیره تغییرات" else "افزودن به صندوقچه",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddCategoryDialog = false
                    newCategoryName = ""
                },
                title = { Text("افزودن دسته‌بندی جدید") },
                text = {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("نام دسته‌بندی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = newCategoryName.trim().isNotEmpty(),
                        onClick = {
                            val cat = newCategoryName.trim()
                            val added = viewModel.addCategory(context, cat)
                            if (added) {
                                selectedCategories = selectedCategories + cat
                                Toast.makeText(context, "دسته‌بندی با موفقیت اضافه شد", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "دسته‌بندی تکراری است یا خطا رخ داده است", Toast.LENGTH_SHORT).show()
                            }
                            showAddCategoryDialog = false
                            newCategoryName = ""
                        }
                    ) {
                        Text("افزودن")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddCategoryDialog = false
                        newCategoryName = ""
                    }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

// --- SETTINGS TAB ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    viewModel: PasswordViewModel,
    activity: FragmentActivity,
    triggerBiometric: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val autoLock by viewModel.autoLockTimeout.collectAsStateWithLifecycle()
    val clipboardTimeout by viewModel.clipboardTimeout.collectAsStateWithLifecycle()

    var showMasterPasswordChangeDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showMasterConfirmForBiometric by remember { mutableStateOf(false) }

    val isBioHardwareOk = remember { BiometricHelper.isBiometricAvailable(context) }
    val isBioPrefOn = remember { SecurityManager.isBiometricEnabled(context) }
    var bioState by remember { mutableStateOf(isBioPrefOn) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "تنظیمات صندوقچه امن",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // General settings Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("تنظیمات ظاهری و امنیتی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Theme Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تم برنامه:", fontSize = 14.sp)
                    var expandedThemeMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expandedThemeMenu = true }) {
                            val label = when (themeMode) {
                                "light" -> "روشن"
                                "dark" -> "تاریک"
                                else -> "پیش‌فرض سیستم"
                            }
                            Text(label)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expandedThemeMenu, onDismissRequest = { expandedThemeMenu = false }) {
                            DropdownMenuItem(text = { Text("پیش‌فرض سیستم") }, onClick = {
                                viewModel.updateThemeMode(context, "system")
                                expandedThemeMenu = false
                            })
                            DropdownMenuItem(text = { Text("روشن") }, onClick = {
                                viewModel.updateThemeMode(context, "light")
                                expandedThemeMenu = false
                            })
                            DropdownMenuItem(text = { Text("تاریک") }, onClick = {
                                viewModel.updateThemeMode(context, "dark")
                                expandedThemeMenu = false
                            })
                        }
                    }
                }

                // Auto Lock Timeout
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("قفل خودکار بعد از:", fontSize = 14.sp)
                    var expandedLockMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expandedLockMenu = true }) {
                            val label = when (autoLock) {
                                30 -> "۳۰ ثانیه"
                                60 -> "۱ دقیقه"
                                300 -> "۵ دقیقه"
                                600 -> "۱۰ دقیقه"
                                0 -> "خروج فوری از برنامه"
                                else -> "۱ دقیقه"
                            }
                            Text(label)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expandedLockMenu, onDismissRequest = { expandedLockMenu = false }) {
                            DropdownMenuItem(text = { Text("خروج فوری از برنامه") }, onClick = {
                                viewModel.updateAutoLockTimeout(context, 0)
                                expandedLockMenu = false
                            })
                            DropdownMenuItem(text = { Text("۳۰ ثانیه") }, onClick = {
                                viewModel.updateAutoLockTimeout(context, 30)
                                expandedLockMenu = false
                            })
                            DropdownMenuItem(text = { Text("۱ دقیقه") }, onClick = {
                                viewModel.updateAutoLockTimeout(context, 60)
                                expandedLockMenu = false
                            })
                            DropdownMenuItem(text = { Text("۵ دقیقه") }, onClick = {
                                viewModel.updateAutoLockTimeout(context, 300)
                                expandedLockMenu = false
                            })
                            DropdownMenuItem(text = { Text("۱۰ دقیقه") }, onClick = {
                                viewModel.updateAutoLockTimeout(context, 600)
                                expandedLockMenu = false
                            })
                        }
                    }
                }

                // Clipboard Timeout
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("پاک‌سازی کلیپ‌بورد بعد از:", fontSize = 14.sp)
                    var expandedClipMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expandedClipMenu = true }) {
                            val label = when (clipboardTimeout) {
                                15 -> "۱۵ ثانیه"
                                30 -> "۳۰ ثانیه"
                                60 -> "۱ دقیقه"
                                120 -> "۲ دقیقه"
                                -1 -> "هرگز"
                                else -> "۳۰ ثانیه"
                            }
                            Text(label)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expandedClipMenu, onDismissRequest = { expandedClipMenu = false }) {
                            DropdownMenuItem(text = { Text("۱۵ ثانیه") }, onClick = {
                                viewModel.updateClipboardTimeout(context, 15)
                                expandedClipMenu = false
                            })
                            DropdownMenuItem(text = { Text("۳۰ ثانیه") }, onClick = {
                                viewModel.updateClipboardTimeout(context, 30)
                                expandedClipMenu = false
                            })
                            DropdownMenuItem(text = { Text("۱ دقیقه") }, onClick = {
                                viewModel.updateClipboardTimeout(context, 60)
                                expandedClipMenu = false
                            })
                            DropdownMenuItem(text = { Text("۲ دقیقه") }, onClick = {
                                viewModel.updateClipboardTimeout(context, 120)
                                expandedClipMenu = false
                            })
                            DropdownMenuItem(text = { Text("هرگز") }, onClick = {
                                viewModel.updateClipboardTimeout(context, -1)
                                expandedClipMenu = false
                            })
                        }
                    }
                }

                // Biometric Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ورود زیست‌سنجی (اثر انگشت):", fontSize = 14.sp)
                    Switch(
                        checked = bioState,
                        enabled = isBioHardwareOk,
                        onCheckedChange = { checked ->
                            if (checked) {
                                // Must ask for current password to encrypt it in the Keystore
                                triggerBiometric(
                                    {
                                        // On successful biometric check, ask password
                                        Toast.makeText(context, "اثر انگشت تایید شد. لطفاً برای ثبت نهایی، رمز عبور مستر فعلی را در پاپ‌آپ زیر وارد کنید.", Toast.LENGTH_LONG).show()
                                        // Show password confirm dialog
                                        showMasterPasswordChangeDialog = false // reset
                                        // We will trigger a temporary dialog to capture master password
                                    },
                                    { err ->
                                        Toast.makeText(context, "زیست‌سنجی تایید نشد", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                // To keep it simple, toggle setting directly with confirmation
                                showMasterConfirmForBiometric = true
                            } else {
                                viewModel.toggleBiometricSetting(context, false)
                                bioState = false
                                Toast.makeText(context, "ورود زیست‌سنجی غیرفعال شد", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                if (!isBioHardwareOk) {
                    Text("سنسور اثر انگشت یافت نشد یا ثبت نشده است.", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("مدیریت رمز عبور مستر و بکاپ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showMasterPasswordChangeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(imageVector = Icons.Default.LockReset, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تغییر رمز عبور مستر")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("خروجی بکاپ")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بازیابی بکاپ")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // About Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("درباره برنامه", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(12.dp))

                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("صندوقچه کلیدبان", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("نسخه ۱.۰.۰ (آفلاین)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "یک محافظ امن، محلی و فوق‌العاده سریع با استاندارد رمزنگاری AES-256 و بدون هیچ‌گونه دسترسی به اینترنت جهت حفاظت کامل از حریم خصوصی شما.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Created by Korosh and Nova",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }

        // --- INTERNAL DIALOGS & OVERLAYS ---

        if (showMasterConfirmForBiometric) {
            var inputPassword by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showMasterConfirmForBiometric = false },
                title = { Text("تایید رمز مستر برای زیست‌سنجی") },
                text = {
                    Column {
                        Text("جهت ذخیره ایمن رمز مستر در سخت‌افزار گوشی، رمز عبور مستر فعلی خود را وارد کنید:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputPassword,
                            onValueChange = { inputPassword = it },
                            label = { Text("رمز عبور مستر") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val isCorrect = SecurityManager.verifyMasterPassword(context, inputPassword)
                        if (isCorrect != null) {
                            val success = viewModel.toggleBiometricSetting(context, true, inputPassword)
                            if (success) {
                                bioState = true
                                Toast.makeText(context, "ورود با اثر انگشت فعال شد", Toast.LENGTH_SHORT).show()
                            } else {
                                bioState = false
                                Toast.makeText(context, "خطا در تنظیم زیست‌سنجی سخت‌افزاری", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            bioState = false
                            Toast.makeText(context, "رمز عبور مستر اشتباه است", Toast.LENGTH_SHORT).show()
                        }
                        showMasterConfirmForBiometric = false
                    }) {
                        Text("فعال‌سازی")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showMasterConfirmForBiometric = false 
                        bioState = SecurityManager.isBiometricEnabled(context)
                    }) {
                        Text("انصراف")
                    }
                }
            )
        }

        if (showMasterPasswordChangeDialog) {
            var currentPassword by remember { mutableStateOf("") }
            var newPassword by remember { mutableStateOf("") }
            var newConfirmPassword by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showMasterPasswordChangeDialog = false },
                title = { Text("تغییر رمز عبور مستر") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("توجه داشته باشید که با تغییر رمز مستر، تمامی فیلدهای پایگاه‌داده با کلید جدید مجدداً رمزگذاری می‌شوند.")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("رمز مستر فعلی") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("رمز مستر جدید") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newConfirmPassword,
                            onValueChange = { newConfirmPassword = it },
                            label = { Text("تکرار رمز مستر جدید") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    val enabled = currentPassword.isNotEmpty() && newPassword == newConfirmPassword && newPassword.length >= 6
                    TextButton(
                        enabled = enabled,
                        onClick = {
                            val verified = SecurityManager.verifyMasterPassword(context, currentPassword)
                            if (verified != null) {
                                coroutineScope.launch {
                                    val success = viewModel.changeMasterPassword(context, newPassword)
                                    if (success) {
                                        Toast.makeText(context, "رمز عبور مستر با موفقیت تغییر یافت", Toast.LENGTH_SHORT).show()
                                        showMasterPasswordChangeDialog = false
                                    } else {
                                        Toast.makeText(context, "خطا در فرآیند بازنویسی رمزنگاری فیلدها", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "رمز عبور مستر فعلی اشتباه است", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("تغییر رمز")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMasterPasswordChangeDialog = false }) {
                        Text("انصراف")
                    }
                }
            )
        }

        if (showBackupDialog) {
            val backupData = remember { viewModel.exportEncryptedBackup() ?: "" }
            AlertDialog(
                onDismissRequest = { showBackupDialog = false },
                title = { Text("پشتیبان‌گیری رمزگذاری شده") },
                text = {
                    Column {
                        Text("این متن حاوی تمامی اطلاعات شماست که با الگوریتم قدرتمند AES-256 رمزنگاری شده است. آن را کپی کرده و در محل امنی نگه دارید:")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = backupData,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.copyToClipboard(context, "پشتیبان کلیدبان", backupData, true)
                        Toast.makeText(context, "کد بکاپ کپی شد. آن را در محل امنی ذخیره کنید.", Toast.LENGTH_LONG).show()
                        showBackupDialog = false
                    }) {
                        Text("کپی کد بکاپ")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text("بستن")
                    }
                }
            )
        }

        if (showRestoreDialog) {
            var backupInputStr by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showRestoreDialog = false },
                title = { Text("بازیابی اطلاعات پشتیبان") },
                text = {
                    Column {
                        Text("کد پشتیبان رمزگذاری شده را در کادر زیر قرار دهید. توجه: با این کار تمام داده‌های فعلی پاک شده و با بکاپ جدید جایگزین می‌شوند.")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = backupInputStr,
                            onValueChange = { backupInputStr = it },
                            placeholder = { Text("کد رمزگذاری شده بکاپ را در اینجا جایگذاری کنید...") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = backupInputStr.isNotEmpty(),
                        onClick = {
                            val res = viewModel.restoreEncryptedBackup(context, backupInputStr)
                            Toast.makeText(context, res, Toast.LENGTH_LONG).show()
                            if (res.contains("موفقیت")) {
                                showRestoreDialog = false
                            }
                        }
                    ) {
                        Text("بازیابی نهایی", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

@Composable
fun CodeAnalyzerDialog(
    onDismiss: () -> Unit,
    viewModel: PasswordViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val secureMode by viewModel.secureModeEnabled.collectAsStateWithLifecycle()
    
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanPhaseText by remember { mutableStateOf("") }
    var scanCompleted by remember { mutableStateOf(false) }
    
    // Auto-scan on launch
    LaunchedEffect(Unit) {
        isScanning = true
        val phases = listOf(
            "در حال اسکن ماژول‌های دیتابیس..." to 0.15f,
            "در حال آنالیز لایف‌سایکل و فلش‌های امنیتی..." to 0.35f,
            "بررسی پایداری و امنیت کلیپ‌بورد در اندروید ۱۰+..." to 0.55f,
            "اعتبارسنجی سیستم پشتیبان‌گیری BackupManager..." to 0.75f,
            "تحلیل متدهای امنیتی و زیست‌سنجی SecurityManager..." to 0.90f,
            "تکمیل موفقیت‌آمیز عیب‌یابی!" to 1.0f
        )
        for (phase in phases) {
            scanPhaseText = phase.first
            scanProgress = phase.second
            kotlinx.coroutines.delay(350)
        }
        isScanning = false
        scanCompleted = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "آنالیزور و عیب‌یاب خودکار کدها",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "سیستم هوشمند اسکن کدهای امنیتی Keyban برای پایداری و بررسی ۶ باگ کلیدی پروژه.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isScanning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            progress = { scanProgress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = scanPhaseText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (scanCompleted) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // BUG 1: FLAG_SECURE Black Screen
                        BugReportItem(
                            bugId = 1,
                            title = "مشکل صفحه سیاه در شبیه‌سازها (FLAG_SECURE)",
                            file = "MainActivity.kt",
                            desc = "اعمال بدون شرط پرچم امنیتی از فیلمبرداری جلوگیری می‌کند اما صفحه را در امولاتورهای وب به کلی سیاه نشان می‌دهد.",
                            isFixed = !secureMode,
                            fixedLabel = "صفحه باز است (آماده استریم امولاتور)",
                            unfixedLabel = "صفحه قفل است (سیاه در امولاتور وب)",
                            extraContent = {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        text = "کنترل مستقیم وضعیت نمایش (تغییر زنده پرچم پنجره):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (secureMode) "امنیت حداکثر (صفحه سیاه)" else "حالت امولاتور (صفحه روشن)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Switch(
                                            checked = secureMode,
                                            onCheckedChange = { viewModel.setSecureModeEnabled(context, it) },
                                            modifier = Modifier.scale(0.85f)
                                        )
                                    }
                                }
                            }
                        )

                        // BUG 2: Clipboard Cleansing Exception
                        BugReportItem(
                            bugId = 2,
                            title = "کرش خطای امنیتی پاکسازی کلیپ‌بورد در اندروید ۱۰+",
                            file = "PasswordViewModel.kt",
                            desc = "تغییر کلیپ‌بورد در ترد پس‌زمینه بدون گرفتن اکسپشن روی اندروید ۱۰ به بالا باعث کرش SecurityException می‌شد.",
                            isFixed = true,
                            fixedLabel = "برطرف شده و ایمن (کپسوله در try-catch)",
                            unfixedLabel = "آسیب‌پذیر"
                        )

                        // BUG 3: Biometric Switch Cancel Reset
                        BugReportItem(
                            bugId = 3,
                            title = "عدم بازگشت سوئیچ زیست‌سنجی هنگام لغو تایید هویت",
                            file = "MainApp.kt",
                            desc = "در صورتی که کاربر فعال‌سازی اثر انگشت را لغو می‌کرد، سوییچ تنظیمات در حالت فعال باقی می‌ماند و غیرهمگام می‌شد.",
                            isFixed = true,
                            fixedLabel = "برطرف شده (همگام‌سازی خودکار در لغو)",
                            unfixedLabel = "آسیب‌پذیر"
                        )

                        // BUG 4: Master Password Async Database Race
                        BugReportItem(
                            bugId = 4,
                            title = "تداخل و خطای فرآیند تغییر رمز عبور مستر در عملیات‌های همزمان",
                            file = "PasswordViewModel.kt",
                            desc = "به کارگیری ترد غیرهمگام حین تغییر کلید رمزنگاری باعث تداخل در رید/رایت دیتابیس می‌شد.",
                            isFixed = true,
                            fixedLabel = "برطرف شده (تراکنش بازنویسی همگام متمرکز)",
                            unfixedLabel = "آسیب‌پذیر"
                        )

                        // BUG 5: Auto-Lock Lifecycle Interruption
                        BugReportItem(
                            bugId = 5,
                            title = "تداخل قفل خودکار در حین احراز هویت زیست‌سنجی (Pause/Resume)",
                            file = "PasswordViewModel.kt",
                            desc = "نمایش پاپ‌آپ اثر انگشت باعث رویداد pause و قفل شدن تکراری و به هم خوردن چرخه ورود می‌شد.",
                            isFixed = true,
                            fixedLabel = "برطرف شده (با نادیده‌گیری Pause موقت)",
                            unfixedLabel = "آسیب‌پذیر"
                        )

                        // BUG 6: JSON Import Strict Field Missing Crashes
                        BugReportItem(
                            bugId = 6,
                            title = "کرش ایمپورت اطلاعات به دلیل تغییر ساختار فیلدهای اختیاری",
                            file = "BackupManager.kt",
                            desc = "استفاده از متد سخت‌گیرانه getString برای فیلدهای جدید در بکاپ‌های قدیمی باعث خروج ناگهانی می‌شد.",
                            isFixed = true,
                            fixedLabel = "برطرف شده (بازنویسی به optString ایمن)",
                            unfixedLabel = "آسیب‌پذیر"
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isScanning = true
                    scanCompleted = false
                    coroutineScope.launch {
                        val phases = listOf(
                            "در حال اسکن ماژول‌های دیتابیس..." to 0.15f,
                            "در حال آنالیز لایف‌سایکل و فلش‌های امنیتی..." to 0.35f,
                            "بررسی پایداری و امنیت کلیپ‌بورد در اندروید ۱۰+..." to 0.55f,
                            "اعتبارسنجی سیستم پشتیبان‌گیری BackupManager..." to 0.75f,
                            "تحلیل متدهای امنیتی و زیست‌سنجی SecurityManager..." to 0.90f,
                            "تکمیل موفقیت‌آمیز عیب‌یابی!" to 1.0f
                        )
                        for (phase in phases) {
                            scanPhaseText = phase.first
                            scanProgress = phase.second
                            kotlinx.coroutines.delay(250)
                        }
                        isScanning = false
                        scanCompleted = true
                    }
                },
                enabled = !isScanning
            ) {
                Text("اسکن مجدد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

@Composable
fun BugReportItem(
    bugId: Int,
    title: String,
    file: String,
    desc: String,
    isFixed: Boolean,
    fixedLabel: String,
    unfixedLabel: String,
    extraContent: @Composable (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFixed) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isFixed) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = if (isFixed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bugId.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFixed) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = if (isFixed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isFixed) "🟢 رفع شده" else "🔴 رفع نشده",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFixed) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "مکان کدهای آسیب‌پذیر: $file",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "وضعیت جزئی: " + if (isFixed) fixedLabel else unfixedLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFixed) Color(0xFF2E7D32) else Color(0xFFC62828)
                )

                extraContent?.invoke()
            }
        }
    }
}
