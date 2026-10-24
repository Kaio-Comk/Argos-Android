package com.twinfinder.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.twinfinder.app.MainActivity
import com.twinfinder.app.R
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.data.db.CleanMode
import com.twinfinder.app.data.db.SessionStatus
import com.twinfinder.app.databinding.FragmentHomeBinding
import com.twinfinder.app.databinding.ItemModeBinding
import com.twinfinder.app.ui.clean.GroupPickerActivity
import com.twinfinder.app.ui.clean.SessionLauncher
import com.twinfinder.app.ui.InfoActivity
import com.twinfinder.app.ui.StatsActivity
import com.twinfinder.app.ui.trash.TrashActivity
import com.twinfinder.app.util.Links
import com.twinfinder.app.util.Fmt
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var repo: SessionRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = SessionRepository(requireContext())
        val host = requireActivity() as AppCompatActivity
        mode(b.modeMonth, R.drawable.ic_calendar, R.string.mode_month, getString(R.string.mode_month_desc)) {
            startActivity(GroupPickerActivity.intent(host, CleanMode.MONTH))
        }
        mode(b.modeAlbum, R.drawable.ic_album, R.string.mode_album, getString(R.string.mode_album_desc)) {
            startActivity(GroupPickerActivity.intent(host, CleanMode.ALBUM))
        }
        mode(b.modeType, R.drawable.ic_media_type, R.string.mode_type, getString(R.string.mode_type_desc)) {
            startActivity(GroupPickerActivity.intent(host, CleanMode.TYPE))
        }
        mode(b.modeRandom, R.drawable.ic_shuffle, R.string.mode_random, "") {
            viewLifecycleOwner.lifecycleScope.launch { SessionLauncher.startRandom(host, repo) }
        }
        mode(b.modeDuplicates, R.drawable.ic_copy, R.string.mode_duplicates, getString(R.string.mode_duplicates_desc)) {
            (host as? MainActivity)?.select(R.id.nav_duplicates)
        }
        mode(b.modeTrash, R.drawable.ic_delete, R.string.home_trash, getString(R.string.trash_info)) {
            startActivity(Intent(host, TrashActivity::class.java))
        }
        b.learnMore.setOnClickListener { startActivity(InfoActivity.intent(host, R.string.clarification_title, R.string.clarification_text)) }
        b.statsCard.setOnClickListener { startActivity(Intent(host, StatsActivity::class.java)) }
        b.startCleaning.setOnClickListener { startActivity(GroupPickerActivity.intent(host)) }
        b.ratingYes.setOnClickListener {
            prefs().ratingAsked = true
            Links.rate(host)
            b.ratingCard.isVisible = false
        }
        b.ratingNo.setOnClickListener {
            prefs().ratingAsked = true
            Links.feedback(host)
            b.ratingCard.isVisible = false
        }
    }

    private fun prefs() = Prefs(requireContext())

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) refresh()
    }

    private fun refresh() {
        b.modeRandom.desc.text = getString(R.string.mode_random_desc, Prefs(requireContext()).randomCount)
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = repo.stats()
            b.statReviewed.text = stats.reviewed.toString()
            b.statFreed.text = Fmt.size(requireContext(), stats.freedBytes)
            b.ratingCard.isVisible = stats.reviewed >= RATING_AFTER && !prefs().ratingAsked
            b.ratingText.text = getString(R.string.rating_banner, stats.reviewed, Fmt.size(requireContext(), stats.freedBytes))
            b.discovered.text = getString(R.string.home_discovered, repo.library().size)

            val open = repo.sessions(SessionStatus.IN_PROGRESS).firstOrNull()
            b.continueCard.isVisible = open != null
            if (open != null) {
                val c = repo.counts(open.id)
                b.continueDesc.text = getString(R.string.home_continue_desc, open.label, c.pending)
                b.continueCard.setOnClickListener { SessionLauncher.open(requireActivity() as AppCompatActivity, open.id) }
            }
        }
    }

    private fun mode(row: ItemModeBinding, icon: Int, title: Int, desc: String, onClick: () -> Unit) {
        row.icon.setImageResource(icon)
        row.title.setText(title)
        row.desc.text = desc
        row.root.setOnClickListener { onClick() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private companion object {
        /** Itens revisados antes de pedir avaliação. */
        const val RATING_AFTER = 50
    }
}
