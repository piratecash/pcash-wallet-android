package cash.p.terminal

import cash.p.terminal.modules.main.MainActivity

/**
 * Launcher entry point used while the app is running in calculator stealth mode.
 * Inherits MainActivity's behavior unchanged; the only purpose of having a separate
 * Activity class is so the manifest can pin a static `android:label` of "Calculator"
 * for it. The label is then read from the manifest by every Recents UI implementation
 * (AOSP, Pixel Quickstep, MIUI/HyperOS), which ignore runtime setTaskDescription on
 * some ROMs.
 */
class CalculatorEntryActivity : MainActivity()
