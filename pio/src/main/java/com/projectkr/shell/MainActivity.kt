package com.projectkr.shell

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Toast
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageListReader
import com.omarea.krscript.model.PageClickHandler
import com.omarea.krscript.model.PageInfo
import com.omarea.krscript.ui.FileChooserRender
import com.omarea.vtools.FloatMonitor
import com.projectkr.shell.ui.TabIconHelper
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    val progressBarDialog = ProgressBarDialog(this)
    private var handler = Handler()
    private var useHomePage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!KeepShellPublic.checkRoot()) {
            DialogHelper.animDialog(AlertDialog.Builder(this)
                    .setTitle(getString(R.string.need_root_permissions))
                    .setMessage(getString(R.string.need_root_permissions_desc))
                    .setCancelable(false)
                    .setPositiveButton(R.string.btn_confirm, { _, _ ->
                        System.exit(0)
                    }))
            return
        }

        //supportActionBar!!.elevation = 0f
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        setTitle(R.string.app_name)

        val window = window
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            getWindow().decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        //getWindow().setNavigationBarColor(Color.WHITE);

        val krScriptConfig = KrScriptConfigLoader().initFramework(this.applicationContext)


        main_tabhost.setup()
        val tabIconHelper = TabIconHelper(main_tabhost, this)
        useHomePage = krScriptConfig.get(KrScriptConfigLoader.ALLOW_HOME_PAGE) == "1"
        if (useHomePage) {
            tabIconHelper.newTabSpec(getString(R.string.tab_home), getDrawable(R.drawable.tab_home)!!, R.id.main_tabhost_cpu)
        } else {
            main_tabhost_cpu.visibility = View.GONE

        }
        main_tabhost.setOnTabChangedListener {
            tabIconHelper.updateHighlight()
        }

        progressBarDialog.showDialog(getString(R.string.please_wait))
        Thread(Runnable {
            val pages = PageListReader(this.applicationContext).readPageList(krScriptConfig.get(KrScriptConfigLoader.PAGE_LIST_CONFIG)!!)
            val favorites = PageConfigReader(this.applicationContext).readConfigXml(krScriptConfig.get(KrScriptConfigLoader.FAVORITE_CONFIG)!!)
            handler.post {
                progressBarDialog.hideDialog()
                list_pages.setListData(pages, object : PageClickHandler {
                    override fun openPage(pageInfo: PageInfo) {
                        _openPage(pageInfo)
                    }
                })
                list_favorites.setListData(favorites, object : FileChooserRender.FileChooserInterface {
                    override fun openFileChooser(fileSelectedInterface: FileChooserRender.FileSelectedInterface): Boolean {
                        return false
                    }
                })

                if (list_favorites.count > 0) {
                    tabIconHelper.newTabSpec(getString(R.string.tab_favorites), getDrawable(R.drawable.tab_favorites)!!, R.id.main_tabhost_2)
                } else {
                    main_tabhost_2.visibility = View.GONE
                }
                if (list_pages.count > 0) {
                    tabIconHelper.newTabSpec(getString(R.string.tab_pages), getDrawable(R.drawable.tab_pages)!!, R.id.main_tabhost_3)
                } else {
                    main_tabhost_3.visibility = View.GONE
                }
            }
        }).start()

        val home = FragmentHome()
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.main_tabhost_cpu, home)
        transaction.commit()
    }

    fun _openPage(pageInfo: PageInfo) {
        try {
            if (!pageInfo.pageConfigPath.isEmpty()) {
                val intent = Intent(this, ActionPage::class.java)
                intent.putExtra("config", pageInfo.pageConfigPath)
                intent.putExtra("title", pageInfo.title)
                startActivity(intent)
            } else if (!pageInfo.onlineHtmlPage.isEmpty()) {
                val intent = Intent(this, ActionPageOnline::class.java)
                intent.putExtra("config", pageInfo.onlineHtmlPage)
                intent.putExtra("title", pageInfo.title)
                startActivity(intent)
            }
        } catch (ex: java.lang.Exception) {
            Log.e("_openPage", "" + ex.message)
        }
    }

    private fun getDensity(): Int {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        return dm.densityDpi
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.option_menu_info -> {
                val layoutInflater = LayoutInflater.from(this)
                DialogHelper.animDialog(
                        AlertDialog.Builder(this).setView(layoutInflater.inflate(R.layout.dialog_about, null))
                )
            }
            R.id.option_menu_reboot -> {
                DialogHelper.animDialog(AlertDialog.Builder(this)
                        .setTitle(R.string.reboot_confirm)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            KeepShellPublic.doCmdSync(getString(R.string.command_reboot))
                        }
                        .setNegativeButton(R.string.no) { _, _ -> })
            }
            R.id.action_graph -> {
                if (FloatMonitor.isShown == true) {
                    FloatMonitor(this).hidePopupWindow()
                    return false
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (Settings.canDrawOverlays(this)) {
                        FloatMonitor(this).showPopupWindow()
                        Toast.makeText(this, getString(R.string.float_monitor_tips), Toast.LENGTH_LONG).show()
                    } else {
                        //若没有权限，提示获取
                        //val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        //startActivity(intent);
                        val intent = Intent()
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                        intent.data = Uri.fromParts("package", this.packageName, null)
                        Toast.makeText(applicationContext, getString(R.string.permission_float), Toast.LENGTH_LONG).show()
                    }
                } else {
                    FloatMonitor(this).showPopupWindow()
                    Toast.makeText(this, getString(R.string.float_monitor_tips), Toast.LENGTH_LONG).show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
