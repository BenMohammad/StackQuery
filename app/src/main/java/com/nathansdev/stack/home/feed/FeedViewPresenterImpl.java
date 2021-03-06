package com.nathansdev.stack.home.feed;

import com.nathansdev.stack.AppConstants;
import com.nathansdev.stack.AppPreferences;
import com.nathansdev.stack.base.BasePresenter;
import com.nathansdev.stack.data.api.StackExchangeApi;
import com.nathansdev.stack.data.model.Question;
import com.nathansdev.stack.data.model.QuestionsResponse;
import com.nathansdev.stack.error.DisposableSubscriberCallbackWrapper;
import com.nathansdev.stack.home.adapter.QuestionsAdapterRow;
import com.nathansdev.stack.home.adapter.QuestionsAdapterRowDataSet;

import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * implementer class to fetch and add to recyclerview from api.
 */
public class FeedViewPresenterImpl<V extends FeedView> extends BasePresenter<V> implements FeedViewPresenter<V> {

    private StackExchangeApi api;
    private AppPreferences preferences;
    private PublishProcessor<Long> questionsSubject = PublishProcessor.create();
    private CompositeDisposable disposables = new CompositeDisposable();
    private QuestionsAdapterRowDataSet rowDataSet;
    private String type;
    private long page = 1;
    private boolean isLoading = false;

    @Inject
    FeedViewPresenterImpl(StackExchangeApi api, AppPreferences preferences) {
        this.api = api;
        this.preferences = preferences;
    }

    @Override
    public void init(QuestionsAdapterRowDataSet dataset, String filterType) {
        this.rowDataSet = dataset;
        this.type = filterType;
        dataset.addRow(QuestionsAdapterRow.ofLoading());
        getMvpView().showLoading();
        Disposable disposable = questionsSubject
                .onBackpressureDrop()
                .concatMap((Function<Long, Publisher<QuestionsResponse>>) page ->
                        getObservable())
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Timber.e(throwable);
                        removeLoading();
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSubscriberCallbackWrapper<QuestionsResponse>(getMvpView()) {

                    @Override
                    protected void onNextAction(QuestionsResponse response) {
                        isLoading = false;
                        getMvpView().hideLoading();
                        handleQuestionResponse(response);
                    }

                    @Override
                    protected void onCompleted() {

                    }
                });
        disposables.add(disposable);
    }

    private void handleQuestionResponse(QuestionsResponse response) {
        Timber.d("handleQuestionResponse %s", response.hasMore());
        removeLoading();
        List<QuestionsAdapterRow> rows = new ArrayList<>();
        if (response != null && response.questions() != null && !response.questions().isEmpty()) {
            for (Question question : response.questions()) {
                rows.add(QuestionsAdapterRow.ofQuestion(question));
            }
            if (response.hasMore() != null && response.hasMore()) {
                rows.add(QuestionsAdapterRow.ofLoadMore());
            }
        }
        Timber.d("questions rows size %s", rows.size());
        rowDataSet.addAllRows(rows);
        getMvpView().onQuestionsLoaded(rows);
    }

    private void removeLoading() {
        rowDataSet.removeLoading();
        rowDataSet.removeLoadMore();
    }

    @Override
    public void loadQuestions() {
        Timber.d("loadQuestions %s %s", type, page);
        if (!isLoading) {
            isLoading = true;
            questionsSubject.onNext(page);
        }
    }

    @Override
    public void loadNextPage() {
        Timber.d("loadNextPage %s %s", type, page);
        if (!isLoading) {
            isLoading = true;
            page++;
            questionsSubject.onNext(page);
        }
    }

    @Override
    public void cleanUp() {
        disposables.clear();
    }

    private Flowable<QuestionsResponse> getObservable() {
        if (type.equalsIgnoreCase(AppConstants.MY_FEED)) {
            return api.getUsersQuestionsFlowable(preferences.getUserId(), AppConstants.ACTIVITY, AppConstants.SITE, AppConstants.DESC, page, 10)
                    .subscribeOn(Schedulers.io());
        } else {
            return api.getQuestionsFlowable(type, AppConstants.SITE, AppConstants.DESC, page, 20)
                    .subscribeOn(Schedulers.io());
        }
    }
}
