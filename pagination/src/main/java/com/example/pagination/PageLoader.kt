package com.example.pagination

import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PageLoader<T>(var isAutoInit: Boolean = true, var pagedListener: PagedListener<T>) {

    val data: MutableList<T> = mutableListOf()
    val dataLiveData = MutableLiveData<List<T>?>()
    val pageLoadingState = MutableLiveData<PageLoadingStates>()
    var initLoad: Boolean = false
    var loadJob: Job? = null
    var isLimitReached: Boolean = false
    var isLimitedHasBeenShown = false
    var onInvalidateListener: OnInvalidateListener? = null

    init {
        if (isAutoInit) loadInit()
    }

    fun loadInit() {
        loadJob = CoroutineScope(Dispatchers.Default).launch { loadHandler() }
    }

    suspend fun loadHandler() {
        pageLoadingState.postValue(PageLoadingStates.START)
        pageLoadingState.postValue(PageLoadingStates.LOADING)
        if (!initLoad) loadInitialData() else loadNextData()
    }

    fun invalidate(reload: Boolean = true) {
        onInvalidateListener?.invalidate()
        when (pagedListener) {
            is PagedOffSetListener<*> -> {
                (pagedListener as PagedOffSetListener<*>).apply {
                    this.pageOffSet = 0
                }
            }
            is PagedNumberListener<*> -> {
                (pagedListener as PagedNumberListener<*>).page = 0
            }
        }
        data.clear()
        dataLiveData.value = null
        isLimitReached = false
        isLimitedHasBeenShown = false
        loadJob?.cancel()
        initLoad = false
        if (reload) loadJob = CoroutineScope(Dispatchers.Default).launch { loadHandler() }
    }

    private suspend fun loadInitialData() {
        initLoad = true
        val responseData = when (pagedListener) {
            is PagedKeyListener<T> -> (pagedListener as PagedKeyListener<T>).loadInitial()
            is PagedOffSetListener<T> -> (pagedListener as PagedOffSetListener<T>).run {
                loadInitial(pageOffSet, pageLimit).also { pageOffSet += pageLimit }
            }
            else -> (pagedListener as PagedNumberListener<T>).run {
                loadInitial(page).also { page += 1 }
            }
        }
        responseData?.let {
            this.data.addAll(it)
            if (it.count() < pagedListener.pageLimit) isLimitReached = true
        }
        dataLiveData.postValue(this.data)
        pageLoadingState.postValue(PageLoadingStates.SUCCESS)
    }

    private suspend fun loadNextData() {
        val responseData = when (pagedListener) {
            is PagedKeyListener<T> -> (pagedListener as PagedKeyListener<T>).loadNext(key = null)
            is PagedOffSetListener<T> -> (pagedListener as PagedOffSetListener<T>).run {
                loadNext(pageOffSet, pageLimit).also { pageOffSet += pageLimit }
            }
            else -> (pagedListener as PagedNumberListener<T>).run {
                loadNext(page).also { page += 1 }
            }
        }
        responseData?.let {
            this.data.addAll(it)
            if (it.count() < pagedListener.pageLimit) isLimitReached = true
        }
        dataLiveData.postValue(this.data)
        pageLoadingState.postValue(PageLoadingStates.SUCCESS)
    }

    interface PagedListener<T> {
        var pageLimit: Int
    }

    interface PagedKeyListener<T> : PagedListener<T> {
        var key: String
        suspend fun loadInitial(): List<T>?
        suspend fun loadNext(key: String?): List<T>?
    }

    interface PagedOffSetListener<T> : PagedListener<T> {
        var pageOffSet: Int
        suspend fun loadInitial(pageOffSet: Int, pageLimit: Int): List<T>?
        suspend fun loadNext(pageOffSet: Int, pageLimit: Int): List<T>?

    }

    interface PagedNumberListener<T> : PagedListener<T> {
        var page: Int
        suspend fun loadInitial(page: Int): List<T>?
        suspend fun loadNext(page: Int): List<T>?
    }
}

infix fun RecyclerView.attach(pageLoader: PageLoader<*>) {
    if (pageLoader.isAutoInit) pageLoader.loadInit()
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        var layoutManager = when (this@attach.layoutManager) {
            is GridLayoutManager -> this@attach.layoutManager as GridLayoutManager
            is LinearLayoutManager -> this@attach.layoutManager as LinearLayoutManager
            else -> throw ClassNotFoundException("layout manager is not supported ")
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (dy > 0) {
                if (pageLoader.pageLoadingState.value != PageLoadingStates.LOADING) {
                    if (pageLoader.isLimitReached && !pageLoader.isLimitedHasBeenShown) {
//                        Snackbar.make(
//                            recyclerView as View,
//                            "reached end",
//                            Snackbar.LENGTH_SHORT
//                        ).show()
                        pageLoader.isLimitedHasBeenShown = true
                        return
                    }
                    val itemsCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    if (itemsCount - lastVisibleItem - 1 < 10 && pageLoader.pageLoadingState.value != PageLoadingStates.LOADING) {
                        pageLoader.pageLoadingState.value = PageLoadingStates.LOADING
                        pageLoader.loadJob =
                            CoroutineScope(Dispatchers.Default).launch { pageLoader.loadHandler() }
                    }
                }
            }
        }
    })


}

interface OnInvalidateListener {
    fun invalidate()
}

infix fun RecyclerView.detach(pageLoader: PageLoader<*>) {
    pageLoader.loadJob?.cancel()
    this.clearOnScrollListeners()
}

infix fun RecyclerView.invalidate(pageLoader: PageLoader<*>) {
    pageLoader.invalidate()
}

