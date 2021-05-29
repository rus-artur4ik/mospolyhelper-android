package com.mospolytech.mospolyhelper.features.ui.account.classmates

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import com.mospolytech.mospolyhelper.R
import com.mospolytech.mospolyhelper.databinding.FragmentAccountClassmatesBinding
import com.mospolytech.mospolyhelper.domain.account.classmates.model.Classmate
import com.mospolytech.mospolyhelper.features.ui.account.classmates.adapter.ClassmatesAdapter
import com.mospolytech.mospolyhelper.features.ui.account.messaging.MessagingFragment.Companion.DIALOG_ID
import com.mospolytech.mospolyhelper.utils.*
import io.ktor.client.features.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ClassmatesFragment : Fragment(R.layout.fragment_account_classmates) {

    private val viewBinding by viewBinding(FragmentAccountClassmatesBinding::bind)
    private val viewModel by viewModel<ClassmatesViewModel>()

    private val adapter = ClassmatesAdapter()
    private var classmates: List<Classmate> = emptyList()

    private val editor = object : TextWatcher {
        override fun afterTextChanged(p0: Editable?) {
            val classmatesMutable: MutableList<Classmate> = classmates.toMutableList()
            adapter.items = classmatesMutable.filter { predicate ->
                predicate.name.contains(p0.toString(), true)
            }
        }
        override fun beforeTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) { }
        override fun onTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) { }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ClassmatesAdapter.classmatesClickListener = {
            val data = bundleOf(DIALOG_ID to it)
            findNavController().navigate(R.id.action_classmatesFragment_to_messagingFragment, data)
        }

        lifecycleScope.launch {
            viewModel.getInfo()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.recyclerClassmates.adapter = adapter

        viewBinding.swipeClassmates.setOnRefreshListener {
            lifecycleScope.launch {
                viewModel.downloadInfo()
            }
        }

        lifecycleScope.launchWhenResumed {
            viewModel.auth.collect { result ->
                result?.onSuccess {
                    lifecycleScope.launch {
                        viewModel.downloadInfo()
                    }
                }?.onFailure {
                    viewBinding.swipeClassmates.isRefreshing = false
                    viewBinding.progressLoading.gone()
                    Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show()
                }?.onLoading {
                    if (!viewBinding.swipeClassmates.isRefreshing)
                        viewBinding.progressLoading.show()
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            viewModel.classmates.collect { result ->
                result.onSuccess {
                    viewBinding.swipeClassmates.isRefreshing = false
                    viewBinding.progressLoading.gone()
                    classmates = it
                    if (viewBinding.editSearchClassmate.text.isNotEmpty()) {
                        val classmatesMutable: MutableList<Classmate> = classmates.toMutableList()
                        adapter.items = classmatesMutable.filter { predicate ->
                            predicate.name.contains(viewBinding.editSearchClassmate.text.toString(), true)
                        }
                    } else {
                        adapter.items = classmates
                    }
                }.onFailure {
                    viewBinding.swipeClassmates.isRefreshing = false
                    viewBinding.progressLoading.gone()
                    if (it is ClientRequestException) {
                        if (it.response.status.value == 401) {
                            lifecycleScope.launch {
                                viewModel.refresh()
                            }
                        }
                    } else
                        Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show()
                }.onLoading {
                    if (!viewBinding.swipeClassmates.isRefreshing)
                        viewBinding.progressLoading.show()
                }
            }
        }

    }

    override fun onResume() {
        viewBinding.editSearchClassmate.addTextChangedListener(editor)
        super.onResume()
    }

    override fun onPause() {
        viewBinding.editSearchClassmate.removeTextChangedListener(editor)
        super.onPause()
    }

    override fun onDestroy() {
        ClassmatesAdapter.classmatesClickListener = null
        super.onDestroy()
    }
}