package org.spsl.evtracker.ui.charts

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ChartsPagerAdapter(host: Fragment) : FragmentStateAdapter(host) {

    private val tabs = listOf(
        ChartsTabFragment.TabKind.TREND,
        ChartsTabFragment.TabKind.MONTHLY_KWH,
        ChartsTabFragment.TabKind.MONTHLY_COST,
        ChartsTabFragment.TabKind.AC_DC,
        ChartsTabFragment.TabKind.LOCATIONS,
    )

    fun tabKindAt(position: Int): ChartsTabFragment.TabKind = tabs[position]

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment =
        ChartsTabFragment.newInstance(tabs[position])
}
