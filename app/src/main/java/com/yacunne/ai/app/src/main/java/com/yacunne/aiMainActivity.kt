
@Composable
fun SettingsScreen(
    settings: AppSettings,
    userName: String,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onEditName: () -> Unit,
    onClearAll: () -> Unit,
    onExport: () -> Unit,
    onAbout: () -> Unit,
    onConnect: () -> Unit
) {
    val P = LocalPal.current
    val authCtx = LocalContext.current

    var custom by remember { mutableStateOf(settings.custom) }
    var uiLangOpen by remember { mutableStateOf(false) }

    // Konti y'uwukoresha
    var accountEmail by remember {
        mutableStateOf(AuthStore.email(authCtx))
    }

    val uiOptions = listOf("auto" to tx("lang_auto")) + UI_LANGS
    val uiLabel = uiOptions.firstOrNull {
        it.first == settings.uiLang
    }?.second ?: tx("lang_auto")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(P.panel)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = tx("back"),
                tint = P.panelText,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(10.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                tx("settings"),
                color = P.panelText,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {

            // ACCOUNT
            SectionTitle("Konti / Account")

            SettingRow(
                tx("profile_name_hint"),
                if (userName.isBlank()) tx("name_unset") else userName,
                P.panelText
            ) {
                onEditName()
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (accountEmail.isNotBlank()) {
                SettingRow(
                    "Email account",
                    accountEmail,
                    P.panelText,
                    null
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingRow(
                    "Sign out",
                    "Sohoka muri konti yawe",
                    Color(0xFFDC2626)
                ) {
                    AuthStore.signOut(authCtx)
                    accountEmail = ""
                }
            } else {
                SettingRow(
                    "Guest account",
                    "Nta konti ya email yinjiye muri Yacunne AI.",
                    P.panelText,
                    null
                )
            }

            // LANGUAGE
            SectionTitle(tx("sec_language"))

            LangPicker(
                tx("app_language"),
                uiLabel,
                uiLangOpen,
                { uiLangOpen = !uiLangOpen },
                uiOptions,
                settings.uiLang
            ) {
                onChange(settings.copy(uiLang = it))
                uiLangOpen = false
            }

            // APPEARANCE
            SectionTitle(tx("sec_appearance"))

            Text(
                tx("color_mode"),
                color = P.panelText,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingChip(
                    tx("mode_dark"),
                    settings.theme == "dark",
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(theme = "dark"))
                }

                SettingChip(
                    tx("mode_light"),
                    settings.theme == "light",
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(theme = "light"))
                }

                SettingChip(
                    tx("mode_system"),
                    settings.theme == "system",
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(theme = "system"))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                tx("text_size"),
                color = P.panelText,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingChip(
                    tx("size_s"),
                    settings.fontSize == 0,
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(fontSize = 0))
                }

                SettingChip(
                    tx("size_m"),
                    settings.fontSize == 1,
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(fontSize = 1))
                }

                SettingChip(
                    tx("size_l"),
                    settings.fontSize == 2,
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(fontSize = 2))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                tx("size_preview"),
                color = P.panelText,
                fontSize = (
                    if (settings.fontSize == 0) 14
                    else if (settings.fontSize == 2) 18
                    else 16
                ).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(P.panelCard)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )

            // ANSWERS
            SectionTitle(tx("sec_answers"))

            Text(
                tx("answer_style"),
                color = P.panelText,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingChip(
                    tx("style_short"),
                    settings.style == "short",
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(style = "short"))
                }

                SettingChip(
                    tx("style_normal"),
                    settings.style == "normal",
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(style = "normal"))
                }

                SettingChip(
                    tx("style_long"),
                    settings.style == "long",
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(style = "long"))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                tx("intelligence"),
                color = P.panelText,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingChip(
                    tx("intel_fast"),
                    settings.model == "fast",
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(model = "fast"))
                }

                SettingChip(
                    tx("intel_pro"),
                    settings.model == "pro",
                    Modifier.weight(1f)
                ) {
                    onChange(settings.copy(model = "pro"))
                }
            }

            Text(
                tx("intel_note"),
                color = P.panelMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                tx("custom_title"),
                color = P.panelText,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            TextField(
                value = custom,
                onValueChange = {
                    custom = it.take(500)
                    onChange(settings.copy(custom = custom))
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text(tx("custom_hint"), color = P.panelMuted)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = P.panelCard,
                    unfocusedContainerColor = P.panelCard,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = P.panelText,
                    unfocusedTextColor = P.panelText,
                    cursorColor = P.accent
                )
            )

            Text(
                tx("custom_note"),
                color = P.panelMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )

            // CHATS
            SectionTitle(tx("sec_chats"))

            SettingRow(
                tx("export_title"),
                tx("export_sub"),
                P.panelText
            ) {
                onExport()
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingRow(
                tx("clear_all_title"),
                tx("clear_all_sub"),
                Color(0xFFDC2626)
            ) {
                onClearAll()
            }

            // CONNECT
            SectionTitle(tx("connectors_title"))

            SettingRow(
                tx("connect_title"),
                tx("connect_sub"),
                P.panelText
            ) {
                onConnect()
            }

            // ABOUT
            SectionTitle(tx("sec_about"))

            SettingRow(
                tx("about_title"),
                tx("about_sub"),
                P.panelText
            ) {
                onAbout()
            }
        }
    }
}
