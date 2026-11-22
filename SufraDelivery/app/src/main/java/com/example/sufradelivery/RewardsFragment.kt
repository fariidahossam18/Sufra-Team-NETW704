package com.example.sufradelivery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.sufradelivery.databinding.FragmentRewardsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

/**
 * Rewards tab (Delivery app, Milestone 4 bonus).
 * Shows the points (live), progress to the next reward, and converts 100 points to EGP 10 credit
 * that the volunteer can spend at checkout in the Sufra (Buyer) app with the same account.
 */
class RewardsFragment : Fragment() {

    private var _binding: FragmentRewardsBinding? = null
    private val binding get() = _binding!!

    private var userRef: DatabaseReference? = null
    private var points = 0L

    private val profileListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b = _binding ?: return
            points = (snapshot.child("deliveryPoints").value as? Number)?.toLong() ?: 0L
            val credit = (snapshot.child("credit").value as? Number)?.toDouble() ?: 0.0
            b.tvBigPoints.text = points.toString()
            val towardsNext = (points % DeliveryRepository.POINTS_FOR_REWARD).toInt()
            b.progressPoints.setProgressCompat(
                if (points >= DeliveryRepository.POINTS_FOR_REWARD) 100 else towardsNext, true
            )
            b.tvNextReward.text = if (points >= DeliveryRepository.POINTS_FOR_REWARD) "You can get a reward now! 🎉"
            else "${DeliveryRepository.POINTS_FOR_REWARD - points} points to your next EGP 10"
            b.btnConvert.isEnabled = points >= DeliveryRepository.POINTS_FOR_REWARD
            b.tvCreditInfo.text = "Your credit: ${formatPrice(credit)} · use it at checkout in the Sufra app"
        }

        override fun onCancelled(error: DatabaseError) {}
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRewardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uid = FirebaseRefs.auth.currentUser?.uid ?: return
        binding.btnConvert.setOnClickListener { confirmConvert(uid) }
        userRef = FirebaseRefs.userRef(uid).also { it.addValueEventListener(profileListener) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userRef?.removeEventListener(profileListener)
        _binding = null
    }

    private fun confirmConvert(uid: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Get EGP 10 credit?")
            .setMessage("100 of your $points points will become EGP 10 credit for your orders in the Sufra app.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Convert") { _, _ ->
                DeliveryRepository.convert(uid) { error ->
                    toast(error ?: "🎁 EGP 10 added to your credit")
                }
            }
            .show()
    }
}
