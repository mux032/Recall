package com.recall.app.presentation

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.recall.app.R
import com.recall.app.presentation.splash.SplashActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for app navigation and main flows.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AppNavigationTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(SplashActivity::class.java)
    
    @Test
    fun splashScreen_navigatesToOnboarding() {
        // Wait for splash to complete (2 seconds)
        Thread.sleep(2500)
        
        // Verify onboarding screen is displayed
        onView(withId(R.id.view_pager))
            .check(matches(isDisplayed()))
    }
    
    @Test
    fun onboarding_swipeThroughAllScreens() {
        // Wait for splash
        Thread.sleep(2500)
        
        // Swipe through onboarding screens
        onView(withId(R.id.view_pager)).perform(swipeLeft())
        Thread.sleep(500)
        
        onView(withId(R.id.view_pager)).perform(swipeLeft())
        Thread.sleep(500)
        
        // Click Get Started
        onView(withId(R.id.btn_get_started))
            .check(matches(isDisplayed()))
            .perform(click())
        
        // Should navigate to permission screen or home
        Thread.sleep(500)
    }
    
    @Test
    fun bottomNavigation_allTabsAccessible() {
        // Wait for splash and onboarding
        Thread.sleep(3000)
        
        // Click through all bottom navigation tabs
        onView(withId(R.id.navigation_home))
            .check(matches(isDisplayed()))
            .perform(click())
        
        onView(withId(R.id.navigation_search))
            .check(matches(isDisplayed()))
            .perform(click())
        
        onView(withId(R.id.navigation_timeline))
            .check(matches(isDisplayed()))
            .perform(click())
        
        onView(withId(R.id.navigation_categories))
            .check(matches(isDisplayed()))
            .perform(click())
        
        onView(withId(R.id.navigation_settings))
            .check(matches(isDisplayed()))
            .perform(click())
    }
}
