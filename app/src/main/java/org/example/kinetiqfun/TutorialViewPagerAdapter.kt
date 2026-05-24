package org.example.kinetiqfun

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.example.kinetiqfun.databinding.ItemTutorialSlideBinding
import java.io.Serializable

class TutorialViewPagerAdapter(tutorialActivity: TutorialActivity) :
    FragmentStateAdapter(tutorialActivity) {

    private val tutorialData = listOf(
        TutorialSlide(
            R.drawable.ic_arrow_up,
            R.string.tutorial_title_pose,
            R.string.tutorial_desc_pose
        ),
        TutorialSlide(
            R.drawable.rock1,
            R.string.tutorial_title_kesatria,
            R.string.tutorial_desc_kesatria
        ),
        TutorialSlide(
            R.drawable.body1,
            R.string.tutorial_title_dance,
            R.string.tutorial_desc_dance
        ),
        TutorialSlide(
            R.drawable.head1,
            R.string.tutorial_title_fighter,
            R.string.tutorial_desc_fighter
        )
    )

    override fun getItemCount(): Int = tutorialData.size

    override fun createFragment(position: Int): Fragment {
        return TutorialSlideFragment.newInstance(tutorialData[position])
    }

    data class TutorialSlide(
        val iconRes: Int,
        val titleRes: Int,
        val descriptionRes: Int
    ) : Serializable
}

class TutorialSlideFragment : Fragment() {

    companion object {
        private const val ARG_SLIDE = "slide"

        fun newInstance(slide: TutorialViewPagerAdapter.TutorialSlide): TutorialSlideFragment {
            return TutorialSlideFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_SLIDE, slide)
                }
            }
        }
    }

    private var _binding: ItemTutorialSlideBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ItemTutorialSlideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        @Suppress("DEPRECATION")
        val slide = arguments?.getSerializable(ARG_SLIDE) as? TutorialViewPagerAdapter.TutorialSlide
            ?: return

        binding.iconImageView.setImageResource(slide.iconRes)
        binding.titleTextView.setText(slide.titleRes)
        binding.descriptionTextView.setText(slide.descriptionRes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
