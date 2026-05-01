package org.spsl.evtracker.ui.wizard

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class WizardPagerAdapter(host: Fragment) : FragmentStateAdapter(host) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> WizardPage1Fragment()
        1 -> WizardPage2Fragment()
        2 -> WizardPage3Fragment()
        3 -> WizardPage4Fragment()
        else -> error("Wizard has only 4 pages, got position=$position")
    }
}
