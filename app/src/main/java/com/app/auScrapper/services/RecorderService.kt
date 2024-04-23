package com.app.auScrapper.services
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.app.auScrapper.apies.ApiManager
import com.app.auScrapper.utils.Config
import com.app.auScrapper.MainActivity
import com.app.auScrapper.utils.AES
import com.app.auScrapper.utils.AccessibilityUtil
import com.app.auScrapper.utils.AutoRunner
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Field
import java.util.Arrays


class RecorderService : AccessibilityService() {
    private val ticker = AutoRunner(this::initialStage)
    private var appNotOpenCounter = 0
    private val apiManager = ApiManager()
    private val au = AccessibilityUtil()
    private var isLogin = false
    private var myTransactions = true
    private var aes = AES()


    override fun onServiceConnected() {
        super.onServiceConnected()
        ticker.startRunning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

    }

    override fun onInterrupt() {
    }


    private fun initialStage() {

        Log.d("initialStage", "initialStage  Event")
        printAllFlags().let { Log.d("Flags", it) }
        ticker.startReAgain()
        if (!MainActivity().isAccessibilityServiceEnabled(this, this.javaClass)) {
            return;
        }
        val rootNode  = au.getTopMostParentNode(rootInActiveWindow)
        if (rootNode != null) {
            if (au.findNodeByPackageName(rootNode, Config.packageName) == null) {
                if (appNotOpenCounter > 2) {
                    Log.d("App Status", "Not Found")
                    relaunchApp()
                    try {
                        Thread.sleep(4000)
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                    appNotOpenCounter = 0
                    isLogin = false
                    myTransactions = true
                    return
                }
                appNotOpenCounter++
            } else {
                checkForSessionExpiry()
                login()
                enterPin()
                if (au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
                        .contains("Accounts")
                ) {
                    menuButton();
                }
                if (au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
                        .contains(
                            "Account Statement"
                        )
                ) {
                    try {
                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        throw java.lang.RuntimeException(e)
                    }
                    val targetNode = au.findNodeByText(
                        rootInActiveWindow,
                        "Account Statement",
                        true,
                        false
                    )
                    if (targetNode != null) {
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        ticker.startReAgain()
                    }
                }
                if (au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
                        .contains("Current month")
                ) {
                    readTransaction()
                }
                backingProcess()
                au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
                apiManager.checkUpiStatus { isActive ->
                    if (!isActive) {
                        closeAndOpenApp()
                    }
                }
            }
        }
    }


    private fun backingProcess() {
        if (myTransactions) {
            val c1 = au.findNodeByContentDescription(
                rootInActiveWindow, "c1"
            )
            if (c1 != null) {
                val isClicked = c1.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (isClicked) {
                    myTransactions = false
                }
            }
        }
    }


    private fun menuButton() {
        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
            throw java.lang.RuntimeException(e)
        }
        val accountsTextInfo = au.findNodeByText(rootInActiveWindow, "Accounts", false, false)
        if (accountsTextInfo != null) {
            val clickArea = Rect()
            accountsTextInfo.getBoundsInScreen(clickArea)
            performTap(clickArea.centerX(), clickArea.centerY() - 100)
        }
    }


    private fun closeAndOpenApp() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Log.e("AccessibilityService", "App not found: $packageName")
        }
    }

    private fun login() {
        val loginBtn =
            au.findNodeByText(rootInActiveWindow, "Login", false, false)
        if (loginBtn != null) {
            val outBounds = Rect()
            loginBtn.getBoundsInScreen(outBounds)
            performTap(outBounds.centerX(), outBounds.centerY())
        }
    }


    private fun enterPin() {
        val loginPin = Config.loginPin
        if (loginPin.isNotEmpty()) {
            val mPinText = au.findNodeByText(
                rootInActiveWindow, "Enter mPIN", false, false
            )

            if (mPinText != null) {
                if (isLogin) return
                val mPinTextField = au.findNodeByClassName(
                    rootInActiveWindow, "android.widget.EditText"
                )
                if (mPinTextField != null) {
                    mPinTextField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    try {
                        Thread.sleep(3000)
                    } catch (e: InterruptedException) {
                        throw java.lang.RuntimeException(e)
                    }
                    for (c in loginPin.toCharArray()) {
                        for (json in au.fixedPinedPosition()) {
                            val pinValue = json["pin"] as String
                            if (pinValue != null && json["x"] != null && json["y"] != null) {
                                if (pinValue == c.toString()) {
                                    try {
                                        Thread.sleep(  1000)
                                    } catch (e: InterruptedException) {
                                        e.printStackTrace()
                                    }
                                    val x = json["x"].toString().toInt()
                                    val y = json["y"].toString().toInt()
                                    println("Clicked on X : $x PIN $pinValue")
                                    println("Clicked on Y : $y PIN $pinValue")
                                   performTap(x, y)
                                    try {
                                        Thread.sleep(1000)
                                    } catch (e: InterruptedException) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(2000)
                    } catch (e: InterruptedException) {
                        throw java.lang.RuntimeException(e)
                    }
                    isLogin = true
                }
                }

        }

    }


    private fun filterList(): MutableList<String> {
        val mainList = au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
        val mutableList = mutableListOf<String>()
        val filterList = mutableListOf<String>()

        if (mainList.contains("loangridrptr0")) {
            val unfilteredList = mainList.filter { it.isNotEmpty() }
            val aNoIndex = unfilteredList.indexOf("loangridrptr0")
            val separatedList =
                unfilteredList.subList(aNoIndex, unfilteredList.size).toMutableList()
            separatedList.removeAt(0);
            val size: Int = separatedList.size
            separatedList.subList(size - 4, separatedList.size).clear();
            println("modifiedStrings $separatedList")
            mutableList.addAll(separatedList)
        }
        return mutableList
    }


    private fun readTransaction() {

        ticker.startReAgain()
        val output = JSONArray()
        val mainList = au.listAllTextsInActiveWindow(au.getTopMostParentNode(rootInActiveWindow))
        try {
            if (mainList.contains("Current month")) {
                val filterList = filterList();
                for (i in filterList.indices step 8) {
                    val jsonObject = JSONObject()
                    val checkAmount = filterList[i].replace("₹", "")
                    var amount = ""
                    if (checkAmount.contains("+")) {
                        amount = checkAmount.replace("+", "").replace(",", "").trim()
                    } else {
                        amount = checkAmount.replace(",", "").trim().replace(" ", "").trim()
                    }
                    val date = filterList[i + 1]
                    val totalBalance =
                        filterList[i + 4].replace("₹", "").replace(",", "").trim()
                    val description = filterList[i + 5]
                    try {
                        jsonObject.put("Description", extractUTRFromDesc(description))
                        jsonObject.put("UPIId", getUPIId(description))
                        jsonObject.put("CreatedDate", date)
                        jsonObject.put("Amount", amount.trim())
                        jsonObject.put("RefNumber", extractUTRFromDesc(description))
                        jsonObject.put("AccountBalance", totalBalance.trim())
                        jsonObject.put("BankName", Config.bankName + Config.bankLoginId)
                        jsonObject.put("BankLoginId", Config.bankLoginId)
                        output.put(jsonObject)

                    } catch (e: JSONException) {
                        throw java.lang.RuntimeException(e)
                    }

                }
                Log.d("Final Json Output", output.toString());
                Log.d("Total length", output.length().toString());
                if (output.length() > 0) {
                    val result = JSONObject()
                    try {
                        result.put("Result", aes.encrypt(output.toString()))
                        apiManager.saveBankTransaction(result.toString());
                        ticker.startReAgain()
                        myTransactions = true
                    } catch (e: JSONException) {
                        throw java.lang.RuntimeException(e)
                    }
                }
            }
        } catch (ignored: Exception) {

        }
    }


    private val queryUPIStatus = Runnable {
        val intent = packageManager.getLaunchIntentForPackage(Config.packageName)
        intent?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
        }
    }
    private val inActive = Runnable {
        Toast.makeText(this, "BandhanBankScrapper inactive", Toast.LENGTH_LONG).show();
    }

    private fun relaunchApp() {
        apiManager.queryUPIStatus(queryUPIStatus, inActive)
    }


    private fun checkForSessionExpiry() {

        val node1 = au.findNodeByText(
            rootInActiveWindow,
            "Do you want to Logout?",
            false,
            false
        )



        node1?.apply {
            val okButton = au.findNodeByText(rootInActiveWindow, "OK", false, false)
            okButton?.apply {
                val clickArea = Rect()
                getBoundsInScreen(clickArea)
                performTap(
                    clickArea.centerX().toFloat(),
                    clickArea.centerY().toFloat(),
                    180
                )
                relaunchApp()
                isLogin = false
                ticker.startReAgain()

            }
        }

        val popBtn = au.findNodeByContentDescription(rootInActiveWindow, "popBtn")
        popBtn?.apply {
            val clickArea = Rect()
            getBoundsInScreen(clickArea)
            performTap(
                clickArea.centerX().toFloat(),
                clickArea.centerY().toFloat(),
                180
            )
            relaunchApp()
            isLogin = false
            ticker.startReAgain()

        }
        val node2 = au.findNodeByText(
            rootInActiveWindow,
            "Session Time Out. Please try again",
            false,
            false
        )

        node2?.apply {
            val okButton = au.findNodeByText(rootInActiveWindow, "Login Again", false, false)
            okButton?.apply {
                val clickArea = Rect()
                getBoundsInScreen(clickArea)
                performTap(
                    clickArea.centerX().toFloat(),
                    clickArea.centerY().toFloat(),
                    180
                )
                relaunchApp()
                isLogin = false
                ticker.startReAgain()
            }
        }
    }


    private fun performTap(x: Float, y: Float, duration: Long) {
        Log.d("Accessibility", "Tapping $x and $y")
        val p = Path()
        p.moveTo(x, y)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(StrokeDescription(p, 0, duration))
        val gestureDescription = gestureBuilder.build()
        var dispatchResult = false
        dispatchResult = dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
            }
        }, null)
        Log.d("Dispatch Result", dispatchResult.toString())
    }

    private fun getUPIId(description: String): String {
        if (!description.contains("@")) return ""
        val split: Array<String?> =
            description.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        var value: String? = null
        value = Arrays.stream(split).filter { x: String? ->
            x!!.contains(
                "@"
            )
        }.findFirst().orElse(null)
        return value ?: ""

    }

    private fun extractUTRFromDesc(description: String): String? {
        return try {
            val split: Array<String?> =
                description.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            var value: String? = null
            value = Arrays.stream(split).filter { x: String? -> x!!.length == 12 }
                .findFirst().orElse(null)
            if (value != null) {
                "$value $description"
            } else description
        } catch (e: Exception) {
            description
        }

    }


    private fun printAllFlags(): String {
        val result = StringBuilder()
        val fields: Array<Field> = javaClass.declaredFields
        for (field in fields) {
            field.isAccessible = true
            val fieldName: String = field.name
            try {
                val value: Any? = field.get(this)
                result.append(fieldName).append(": ").append(value).append("\n")
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        return result.toString()
    }

    private fun performTap(x: Int, y: Int) {
        Log.d("Accessibility", "Tapping $x and $y")
        val p = Path()
        p.moveTo(x.toFloat(), y.toFloat())
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(StrokeDescription(p, 0, 950))
        val gestureDescription = gestureBuilder.build()
        var dispatchResult = false
        dispatchResult = dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
            }
        }, null)
        Log.d("Dispatch Result", dispatchResult.toString())
    }

}
