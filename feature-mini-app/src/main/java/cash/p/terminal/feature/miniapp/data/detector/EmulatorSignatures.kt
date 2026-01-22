package cash.p.terminal.feature.miniapp.data.detector

internal object EmulatorSignatures {

    val FINGERPRINT_PREFIXES = listOf("generic", "unknown")

    val MODEL_INDICATORS = listOf(
        "google_sdk",
        "droid4x",
        "Emulator",
        "Android SDK built for x86",
        "sdk"
    )

    val MANUFACTURERS = listOf(
        "Genymotion",
        "unknown",
        "sdk",
        "andy",
        "google_sdk",
        "droid4x",
        "nox",
        "sdk_x86",
        "vbox86p"
    )

    val HARDWARE_NAMES = listOf(
        "goldfish",
        "vbox86",
        "nox",
        "ranchu",
        "VM_x86",
        "intel",
        "amd",
        "x86"
    )

    val PRODUCTS = listOf(
        "sdk",
        "google_sdk",
        "sdk_x86",
        "vbox86p",
        "nox",
        "andy",
        "emu64x",
        "sdk_gphone64_arm64"
    )

    val BOARDS = listOf(
        "unknown",
        "goldfish_arm64"
    )

    val DEVICES = listOf(
        "generic",
        "generic_x86_64",
        "vbox86p",
        "emu64x",
        "generic_arm64",
        "andy",
        "droid4x",
        "nox"
    )

    // Substrings to detect in BOARD, BOOTLOADER, SERIAL, PRODUCT, HARDWARE
    const val NOX_INDICATOR = "nox"

    // Properties where we check for exact value match
    val QEMU_PROPERTY_EXACT = mapOf(
        "ro.kernel.qemu" to "1",
        "ro.product.device" to "generic",
        "ro.bootloader" to "unknown",
        "ro.bootmode" to "unknown"
    )

    // Properties where we check if value contains expected substring
    val QEMU_PROPERTY_CONTAINS = mapOf(
        "ro.hardware" to listOf("goldfish", "ranchu"),
        "ro.product.model" to listOf("sdk"),
        "ro.product.name" to listOf("sdk")
    )

    val QEMU_PROPERTY_EXISTENCE = listOf(
        "init.svc.qemud",
        "init.svc.qemu-props",
        "qemu.hw.mainkeys",
        "qemu.sf.fake_camera",
        "qemu.sf.lcd_density",
        "ro.kernel.android.qemud",
        "ro.kernel.qemu.gles",
        "ro.serialno"
    )

    // ARM translation detection properties
    val ARM_TRANSLATION_PROPERTIES = mapOf(
        "ro.dalvik.vm.isa.arm" to "x86",
        "ro.dalvik.vm.isa.arm64" to "x86_64"
    )

    // ARM translation library files
    val ARM_TRANSLATION_FILES = listOf(
        "/system/lib/libhoudini.so",
        "/system/lib64/libhoudini.so"
    )

    // CPU architecture property
    const val CPU_ABI_PROPERTY = "ro.product.cpu.abilist"
    const val X86_INDICATOR = "x86"

    // Native bridge property (ARM translation)
    const val NATIVE_BRIDGE_PROPERTY = "ro.dalvik.vm.native.bridge"

    // Mount indicators
    const val VBOXSF_INDICATOR = "vboxsf"

    val EMULATOR_FILES = listOf(
        // QEMU
        "/dev/socket/qemud",
        "/dev/qemu_pipe",
        // Genymotion
        "/dev/socket/genyd",
        "/dev/socket/baseband_genyd",
        // Nox
        "/system/bin/nox-prop",
        "/system/bin/noxd",
        "/system/lib/libnoxd.so",
        "/system/lib/libnoxspeedup.so",
        "/system/etc/init.nox.sh",
        "/system/bin/nox",
        "/system/bin/nox-vbox-sf",
        "fstab.nox",
        "init.nox.rc",
        "ueventd.nox.rc",
        // BlueStacks
        "/data/.bluestacks.prop",
        "/data/.bstconf.prop",
        "/dev/bst_gps",
        "/dev/bst_ime",
        "/dev/bstgyro",
        "/dev/bstmegn",
        "/sys/module/bstpgaipc",
        "/sys/module/bstsensor",
        "/system/priv-app/com.bluestacks.bstfolder.apk",
        "/system/bin/bstfolder",
        "/system/bin/bstfolderd",
        "/system/bin/bstsyncfs",
        "/system/xbin/bstk",
        "/system/xbin/bstk/su",
        "/mnt/windows/BstSharedFolder",
        "/data/data/com.bluestacks.bstfolder",
        "/data/data/com.bluestacks.appmart",
        "/data/data/com.bluestacks.home",
        "/data/data/com.bluestacks.launcher",
        // LDPlayer
        "/system/bin/ldinit",
        "/system/bin/ldmountsf",
        "/system/app/LDAppStore/LDAppStore.apk",
        "/data/data/com.ldmnq.launcher3/files/launcher.preferences",
        "/data/data/com.android.ld.appstore",
        "/system/lib/hw/gps.ld.so",
        "/system/lib/hw/sensors.ld.so",
        "/system/lib/libldutils.so",
        // MEmu
        "/dev/memufp",
        "/dev/memuguest",
        "/dev/memuuser",
        "/system/lib/memuguest.ko",
        // Andy
        "fstab.andy",
        "ueventd.andy.rc",
        // Phoenix OS
        "/system/phoenixos",
        "/system/xbin/phoenix_compat",
        "/data/system/phoenixlog.addr",
        // VirtualBox
        "/lib/vboxguest.ko",
        "/lib/vboxsf.ko",
        "/sys/module/vboxsf",
        "/sys/module/vboxpcismv",
        // x86 emulators
        "ueventd.android_x86.rc",
        "x86.prop",
        "fstab.vbox86",
        "init.vbox86.rc",
        "ueventd.vbox86.rc",
        "ueventd.ttVM_x86.rc",
        "init.ttVM_x86.rc",
        "fstab.ttVM_x86",
        // Droid4x
        "/system/bin/droid4x-vbox-sf",
        "/system/bin/droid4x",
        // Koplayer
        "/data/data/com.koplay.launcher",
        "/system/bin/KOPLAYER.ini",
        "/system/bin/androidVM-vbox-sf",
        // Nemu
        "/sys/module/nemusf",
        // Genymotion
        "/system/bin/genybaseband",
        // Additional BlueStacks (from reveny)
        "/system/bin/bstsvcmgrtest",
        "/system/bin/bstshutdown",
        "/system/bin/bstime",
        "/system/bin/bstshutdown_core",
        "/boot/bstmods/bstpgaipc.ko",
        "/boot/bstmods/bstaudio.ko",
        "/boot/bstmods/bstcamera.ko",
        "/boot/bstmods/bstvmsg.ko",
        "/boot/bstmods/bstinput.ko",
        "/boot/bstsetup.env",
        "/boot/bin/bstreport",
        "/boot/bin/bstconf",
        "/boot/bstsetconf.sh",
        // Additional paths
        "/mnt/windows",
        "/sys/module/mg"
    )

    val EMULATOR_PACKAGES = listOf(
        "com.bluestacks",
        "com.bignox.app",
        "com.google.android.launcher.layouts.genymotion",
        "com.ldmnq.launcher3",
        "com.koplay.launcher",
        "com.bluestacks.home",
        "com.bluestacks.appmart",
        "com.android.ld.appstore"
    )

    const val EMULATOR_IP = "10.0.2.15"

    val CPU_INFO_EMULATOR_INDICATORS = listOf(
        "hypervisor",
        "goldfish"
    )

    // Host CPU indicators (leaked in emulator cpuinfo)
    val HOST_CPU_INDICATORS = listOf(
        "amd",
        "intel"
    )

    val QEMU_DRIVERS = listOf("goldfish")
}
