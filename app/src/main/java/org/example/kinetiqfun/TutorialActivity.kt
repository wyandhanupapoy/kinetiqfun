package org.example.kinetiqfun

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.example.kinetiqfun.databinding.ActivityTutorialBinding

class TutorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTutorialBinding
    private lateinit var viewPagerAdapter: TutorialViewPagerAdapter
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "KinetiQFunPrefs"
        private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Check if tutorial was already completed
        if (sharedPreferences.getBoolean(KEY_TUTORIAL_COMPLETED, false)) {
            skipTutorial()
            return
        }

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        viewPagerAdapter = TutorialViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val iconRes = when (position) {
                0 -> R.drawable.ic_arrow_up
                1 -> R.drawable.box
                2 -> R.drawable.body1
                3 -> R.drawable.head1
                else -> 0
            }
            if (iconRes != 0) {
                tab.icon = ContextCompat.getDrawable(this@TutorialActivity, iconRes)
            }
        }.attach()
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            SoundManager.playClick()
            val currentItem = binding.viewPager.currentItem
            if (currentItem < viewPagerAdapter.itemCount - 1) {
                binding.viewPager.setCurrentItem(currentItem + 1, true)
            } else {
                completeTutorial()
            }
        }

        binding.btnSkip.setOnClickListener {
            SoundManager.playClick()
            skipTutorial()
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.btnNext.text = if (position == viewPagerAdapter.itemCount - 1) {
                    getString(R.string.get_started)
                } else {
                    getString(R.string.next)
                }
            }
        })
    }

    private fun completeTutorial() {
        sharedPreferences.edit().putBoolean(KEY_TUTORIAL_COMPLETED, true).apply()
        startMainActivity()
    }

    private fun skipTutorial() {
        sharedPreferences.edit().putBoolean(KEY_TUTORIAL_COMPLETED, true).apply()
        startMainActivity()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startMainActivity() {
        val intent = Intent(this, MenuActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
    }

    override fun onBackPressed() {
        // Disable back button during tutorial to ensure completion
        // In a real app, you might want to allow skipping with confirmation
    }
}