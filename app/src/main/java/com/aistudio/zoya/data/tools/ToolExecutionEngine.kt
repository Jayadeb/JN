package com.aistudio.zoya.data.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.util.Log

class ToolExecutionEngine(
    private val context: Context
) {

    fun openApp(packageName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Successfully opened app: $packageName"
            } else {
                "App not found: $packageName"
            }
        } catch (e: Exception) {
            Log.e("ToolExecutionEngine", "Failed to open app: ${e.message}", e)
            "Failed to open app: ${e.message}"
        }
    }

    fun launchAppByName(appName: String): String {
        return try {
            val packageManager = context.packageManager
            val apps = packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            
            var bestMatchPackage: String? = null
            var bestMatchLabel: String? = null
            
            for (app in apps) {
                val label = packageManager.getApplicationLabel(app).toString()
                if (label.equals(appName, ignoreCase = true)) {
                    bestMatchPackage = app.packageName
                    bestMatchLabel = label
                    break
                }
                if (label.contains(appName, ignoreCase = true)) {
                    bestMatchPackage = app.packageName
                    bestMatchLabel = label
                }
            }

            if (bestMatchPackage != null) {
                val intent = packageManager.getLaunchIntentForPackage(bestMatchPackage)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Successfully opened $bestMatchLabel"
                } else {
                    "Found $bestMatchLabel but couldn't create launch intent."
                }
            } else {
                "Could not find an app named '$appName'"
            }
        } catch (e: Exception) {
            Log.e("ToolExecutionEngine", "Failed to launch app: ${e.message}", e)
            "Failed to launch app: ${e.message}"
        }
    }

    fun searchAndCallContact(contactName: String): String {
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"),
                null
            )
            var number: String? = null
            if (cursor != null && cursor.moveToFirst()) {
                number = cursor.getString(0)
                cursor.close()
            }
            if (number != null) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Calling $contactName at $number"
            } else {
                "Contact not found: $contactName"
            }
        } catch (e: Exception) {
            Log.e("ToolExecutionEngine", "Failed to call contact: ${e.message}", e)
            "Failed to call contact: ${e.message}. Do you have CALL_PHONE permission?"
        }
    }

    fun sendWhatsAppMessage(contactName: String, message: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening WhatsApp to send message to $contactName"
        } catch (e: Exception) {
            "Failed to send WhatsApp message: ${e.message}"
        }
    }

    fun sendGmail(recipientEmail: String, subject: String, body: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Gmail to send email to $recipientEmail"
        } catch (e: Exception) {
            "Failed to send Gmail: ${e.message}"
        }
    }

    fun openWebsite(url: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening website: $url"
        } catch (e: Exception) {
            "Failed to open website: ${e.message}"
        }
    }

    fun setAlarm(time: String, label: String): String {
        return try {
            val (hour, minute) = time.split(":").map { it.toInt() }
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Alarm set for $time with label '$label'"
        } catch (e: Exception) {
            "Failed to set alarm: ${e.message}. Use HH:mm format."
        }
    }

    fun openCamera(): String {
        return try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening camera"
        } catch (e: Exception) {
            "Failed to open camera: ${e.message}"
        }
    }

    fun openSettings(): String {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening settings"
        } catch (e: Exception) {
            "Failed to open settings: ${e.message}"
        }
    }

    fun playYouTube(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Searching YouTube for: $query"
        } catch (e: Exception) {
            "Failed to play YouTube: ${e.message}"
        }
    }

    fun toggleWiFi(enable: Boolean): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val intent = Intent(android.provider.Settings.Panel.ACTION_WIFI)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening Wi-Fi settings panel (Direct toggle restricted on this Android version)"
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enable
                "Wi-Fi ${if (enable) "enabled" else "disabled"}"
            }
        } catch (e: Exception) {
            Log.e("ToolExecutionEngine", "Failed to toggle WiFi: ${e.message}", e)
            "Failed to toggle WiFi: ${e.message}"
        }
    }

    fun toggleBluetooth(enable: Boolean): String {
        return try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) return "Bluetooth not supported on this device"
            
            if (enable) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // On Android 12+, we usually need BLUETOOTH_CONNECT. 
                    // Even then, direct enable() is deprecated.
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Opening Bluetooth settings to enable"
                } else {
                    @Suppress("DEPRECATION")
                    bluetoothAdapter.enable()
                    "Bluetooth enabled"
                }
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Opening Bluetooth settings to disable"
                } else {
                    @Suppress("DEPRECATION")
                    bluetoothAdapter.disable()
                    "Bluetooth disabled"
                }
            }
        } catch (e: Exception) {
            Log.e("ToolExecutionEngine", "Failed to toggle Bluetooth: ${e.message}", e)
            "Failed to toggle Bluetooth: ${e.message}"
        }
    }

    fun toggleFlashlight(enable: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
            "Flashlight turned ${if (enable) "on" else "off"}"
        } catch (e: Exception) {
            Log.e("ToolExecutionEngine", "Failed to toggle flashlight: ${e.message}", e)
            "Failed to toggle flashlight: ${e.message}"
        }
    }

    fun goHome(): String {
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Going to home screen"
        } catch (e: Exception) {
            Log.e("ToolExecutionEngine", "Failed to go home: ${e.message}", e)
            "Failed to go home: ${e.message}"
        }
    }

    fun openNotifications(): String {
        return try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
            "Opening notifications"
        } catch (e: Exception) {
            // Fallback: Open notification settings
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening settings"
        }
    }

    fun openGallery(): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.type = "image/*"
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            "Opening gallery"
        } catch (e: Exception) {
            "Failed to open gallery: ${e.message}"
        }
    }

    fun openBrowser(): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening browser"
        } catch (e: Exception) {
            "Failed to open browser: ${e.message}"
        }
    }

    fun sendSms(phoneNumber: String, message: String): String {
        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            "SMS sent to $phoneNumber"
        } catch (e: Exception) {
            Log.e("ToolExecutionEngine", "Failed to send SMS: ${e.message}", e)
            "Failed to send SMS: ${e.message}. Do you have SEND_SMS permission?"
        }
    }

    fun requestIgnoreBatteryOptimizations(): String {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                "Battery optimizations already ignored"
            } else {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Requesting battery optimization exemption"
            }
        } catch (e: Exception) {
            "Failed to request battery optimization: ${e.message}"
        }
    }

    fun setBrightness(level: Int): String {
        // level 0-255
        return try {
            if (android.provider.Settings.System.canWrite(context)) {
                android.provider.Settings.System.putInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    level.coerceIn(0, 255)
                )
                "Brightness set to $level"
            } else {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Permission required: Please allow 'Modify system settings' for Zoya"
            }
        } catch (e: Exception) {
            "Failed to set brightness: ${e.message}"
        }
    }

    fun setVolume(level: Int): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val targetVolume = (level.toFloat() / 100f * maxVolume).toInt().coerceIn(0, maxVolume)
            
            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                targetVolume,
                android.media.AudioManager.FLAG_SHOW_UI
            )
            "Media volume set to $level%"
        } catch (e: Exception) {
            "Failed to set volume: ${e.message}"
        }
    }

    fun toggleDoNotDisturb(enable: Boolean): String {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val filter = if (enable) {
                    android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                } else {
                    android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(filter)
                "Do Not Disturb ${if (enable) "enabled" else "disabled"}"
            } else {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Permission required: Please allow 'Do Not Disturb access' for Zoya"
            }
        } catch (e: Exception) {
            "Failed to toggle Do Not Disturb: ${e.message}"
        }
    }

    fun getInstalledApps(): String {
        return try {
            val apps = context.packageManager.getInstalledApplications(0)
            val appList = apps.joinToString(", ") { it.packageName }
            "Installed apps: ${appList.take(500)}..."
        } catch (e: Exception) {
            "Failed to get installed apps: ${e.message}"
        }
    }

    fun getNetworkStatus(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            if (capabilities == null) return "No active internet connection"
            
            val type = when {
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }
            
            "Connected via $type"
        } catch (e: Exception) {
            "Failed to get network status: ${e.message}"
        }
    }
}
