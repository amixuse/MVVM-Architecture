package com.qingmei2.sample.ui.main.repos

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.MutableLiveData
import com.qingmei2.rhine.ext.lifecycle.bindLifecycle
import com.qingmei2.rhine.ext.livedata.toFlowable
import com.qingmei2.sample.base.BaseViewModel
import com.qingmei2.sample.base.SimpleViewState
import com.qingmei2.sample.common.loadmore.createLiveData
import com.qingmei2.sample.common.loadmore.loadMore
import com.qingmei2.sample.entity.Repo
import com.qingmei2.sample.manager.UserManager
import io.reactivex.Flowable

@SuppressWarnings("checkResult")
class ReposViewModel(
        private val repo: ReposDataSource
) : BaseViewModel() {

    private val events: MutableLiveData<List<Repo>> = MutableLiveData()

    val adapter = RepoListAdapter()

    val sortFunc: MutableLiveData<String> = MutableLiveData()

    val loading: MutableLiveData<Boolean> = MutableLiveData()
    val error: MutableLiveData<Throwable> = MutableLiveData()

    override fun onCreate(lifecycleOwner: LifecycleOwner) {
        super.onCreate(lifecycleOwner)
        sortFunc.toFlowable()
                .distinctUntilChanged()
                .startWith(sortByLetter)
                .bindLifecycle(this)
                .subscribe {
                    initReposList()
                }
    }

    fun initReposList() {
        loadMore { pageIndex ->
            when (pageIndex) {
                1 -> queryReposRefreshAction()
                else -> queryReposAction(pageIndex)
            }.flatMap { state ->
                when (state) {
                    is SimpleViewState.Result -> Flowable.just(state.result)
                    else -> Flowable.empty()
                }
            }
        }.createLiveData(
                enablePlaceholders = false,
                pageSize = 15,
                initialLoadSizeHint = 30
        ).toFlowable()
                .bindLifecycle(this)
                .subscribe { pagedList ->
                    adapter.submitList(pagedList)
                }
    }

    private fun queryReposAction(pageIndex: Int): Flowable<SimpleViewState<List<Repo>>> =
            repo.queryRepos(
                    UserManager.INSTANCE.name,
                    pageIndex, 15,
                    sortFunc.value ?: sortByLetter
            )
                    .map { either ->
                        either.fold({
                            SimpleViewState.error<List<Repo>>(it)
                        }, {
                            SimpleViewState.result(it)
                        })
                    }
                    .onErrorReturn { it -> SimpleViewState.error(it) }

    private fun queryReposRefreshAction(): Flowable<SimpleViewState<List<Repo>>> =
            queryReposAction(1)
                    .startWith(SimpleViewState.loading())
                    .startWith(SimpleViewState.idle())
                    .doOnNext { state ->
                        when (state) {
                            is SimpleViewState.Refreshing -> applyState(isLoading = true)
                            is SimpleViewState.Idle -> applyState()
                            is SimpleViewState.Error -> applyState(error = state.error)
                            is SimpleViewState.Result -> applyState(events = state.result)
                        }
                    }

    private fun applyState(isLoading: Boolean = false,
                           events: List<Repo>? = null,
                           error: Throwable? = null) {
        this.loading.postValue(isLoading)
        this.error.postValue(error)

        this.events.postValue(events)
    }

    companion object {

        const val sortByCreated: String = "created"

        const val sortByUpdate: String = "updated"

        const val sortByLetter: String = "full_name"
    }
}

typealias RepoTransformer = (Repo, Repo) -> Int