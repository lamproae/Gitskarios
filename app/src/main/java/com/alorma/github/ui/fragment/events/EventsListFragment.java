package com.alorma.github.ui.fragment.events;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Html;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.alorma.github.BuildConfig;
import com.alorma.github.R;
import com.alorma.github.UrlsManager;
import com.alorma.github.sdk.bean.dto.response.Commit;
import com.alorma.github.sdk.bean.dto.response.GithubEvent;
import com.alorma.github.sdk.bean.dto.response.Issue;
import com.alorma.github.sdk.bean.dto.response.User;
import com.alorma.github.sdk.bean.dto.response.events.EventType;
import com.alorma.github.sdk.bean.dto.response.events.payload.ForkEventPayload;
import com.alorma.github.sdk.bean.dto.response.events.payload.IssueCommentEventPayload;
import com.alorma.github.sdk.bean.dto.response.events.payload.IssueEventPayload;
import com.alorma.github.sdk.bean.dto.response.events.payload.PullRequestEventPayload;
import com.alorma.github.sdk.bean.dto.response.events.payload.PushEventPayload;
import com.alorma.github.sdk.bean.dto.response.events.payload.ReleaseEventPayload;
import com.alorma.github.sdk.bean.info.RepoInfo;
import com.alorma.github.sdk.services.user.events.GetUserEventsClient;
import com.alorma.github.ui.activity.RepoDetailActivity;
import com.alorma.github.ui.adapter.events.EventAdapter;
import com.alorma.github.ui.fragment.base.PaginatedListFragment;
import com.alorma.github.utils.AttributesUtils;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;
import com.google.gson.Gson;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.octicons_typeface_library.Octicons;
import com.nostra13.universalimageloader.core.ImageLoader;
import io.fabric.sdk.android.Fabric;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import retrofit.RetrofitError;
import retrofit.client.Response;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;

/**
 * Created by Bernat on 03/10/2014.
 */
public class EventsListFragment extends PaginatedListFragment<List<GithubEvent>, EventAdapter>
    implements EventAdapter.EventAdapterListener {

  private String username;

  private ArrayStrings filterNames;
  private ArrayIntegers filterIds;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
    getSavedFilter();
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    addNewAdapter();
  }

  private void addNewAdapter() {
    EventAdapter eventAdapter = new EventAdapter(getActivity(), LayoutInflater.from(getActivity()));
    eventAdapter.setEventAdapterListener(this);
    setAdapter(eventAdapter);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.events_list_fragment, menu);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    MenuItem item = menu.findItem(R.id.events_list_filter);
    if (item != null) {
      item.setIcon(new IconicsDrawable(getActivity(), GoogleMaterial.Icon.gmd_filter_list).colorRes(
          R.color.white).actionBar());
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.events_list_filter:
        EventType[] values = EventType.values();

        String[] names = new String[values.length - 1];

        for (int i = 0; i < values.length; i++) {
          if (values[i] != EventType.Unhandled) {
            names[i] = values[i].name();
          }
        }

        Integer[] ids = null;

        if (filterIds != null) {
          ids = filterIds.toArray(new Integer[filterIds.size()]);
        }

        logAnswers("EVENT_FILTER_CLICK");

        new MaterialDialog.Builder(getActivity()).items(names)
            .itemsCallbackMultiChoice(ids, new MaterialDialog.ListCallbackMultiChoice() {
              @Override
              public boolean onSelection(MaterialDialog dialog, Integer[] which,
                  CharSequence[] text) {
                EventsListFragment.this.filterIds = new ArrayIntegers(Arrays.asList(which));
                List<CharSequence> filterNames = Arrays.asList(text);
                List<String> filters = new ArrayList<>(filterNames.size());
                for (CharSequence filterName : filterNames) {
                  filters.add(String.valueOf(filterName));
                }
                EventsListFragment.this.filterNames = new ArrayStrings(filters);
                saveFilter();
                executeFromFilter();
                logAnswers("EVENT_FILTER_APPLIED");
                return false;
              }
            })
            .positiveText(R.string.ok)
            .neutralText(R.string.clear_filters)
            .callback(new MaterialDialog.ButtonCallback() {
              @Override
              public void onNeutral(MaterialDialog dialog) {
                super.onNeutral(dialog);
                EventsListFragment.this.filterIds = null;
                EventsListFragment.this.filterNames = null;
                clearSavedFilter();
                executeFromFilter();
                logAnswers("EVENT_FILTER_CLEAR");
              }
            })
            .show();
        break;
    }

    return true;
  }

  private void logAnswers(String event) {
    if (BuildConfig.DEBUG && Fabric.isInitialized()) {
      Answers.getInstance().logContentView(new ContentViewEvent().putContentName(event));
    }
  }

  public void getSavedFilter() {
    SharedPreferences shared = getActivity().getSharedPreferences("FILTERS", Context.MODE_PRIVATE);

    Gson gson = new Gson();

    ArrayList events_filter =
        gson.fromJson(shared.getString("EVENTS_FILTER", null), ArrayList.class);

    if (events_filter != null) {
      EventsListFragment.this.filterNames = new ArrayStrings();
      for (Object o : events_filter) {
        EventsListFragment.this.filterNames.add(String.valueOf(o));
      }
    }

    ArrayList events_filter_ids =
        gson.fromJson(shared.getString("EVENTS_FILTER_IDS", null), ArrayList.class);

    if (events_filter_ids != null) {
      EventsListFragment.this.filterIds = new ArrayIntegers();
      for (Object o : events_filter_ids) {
        EventsListFragment.this.filterIds.add(Double.valueOf(String.valueOf(o)).intValue());
      }
    }
  }

  private class ArrayStrings extends ArrayList<String> {

    public ArrayStrings(List<String> filterNames) {
      super(filterNames);
    }

    public ArrayStrings() {

    }
  }

  private class ArrayIntegers extends ArrayList<Integer> {

    public ArrayIntegers(List<Integer> filterIds) {
      super(filterIds);
    }

    public ArrayIntegers() {

    }
  }

  private void saveFilter() {
    if (filterNames != null && filterIds != null) {
      SharedPreferences shared =
          getActivity().getSharedPreferences("FILTERS", Context.MODE_PRIVATE);

      Gson gson = new Gson();
      SharedPreferences.Editor edit = shared.edit();
      edit.putString("EVENTS_FILTER", gson.toJson(new ArrayStrings(filterNames)));
      edit.putString("EVENTS_FILTER_IDS", gson.toJson(new ArrayIntegers(filterIds)));
      edit.apply();
    }
  }

  private void clearSavedFilter() {
    SharedPreferences shared = getActivity().getSharedPreferences("FILTERS", Context.MODE_PRIVATE);

    SharedPreferences.Editor edit = shared.edit();
    edit.remove("EVENTS_FILTER");
    edit.remove("EVENTS_FILTER_IDS");
    edit.apply();
  }

  private void executeFromFilter() {
    startRefresh();
    addNewAdapter();
    executeRequest();
  }

  @NonNull
  private Func1<GithubEvent, Boolean> getFilterNames() {
    return new Func1<GithubEvent, Boolean>() {
      @Override
      public Boolean call(GithubEvent githubEvent) {
        return (filterNames != null && !filterNames.isEmpty()) ? filterNames.contains(
            githubEvent.type.name()) : checkEventHandled(githubEvent);
      }
    };
  }

  private Observer<GithubEvent> subscriber = new Observer<GithubEvent>() {
    @Override
    public void onCompleted() {
      stopRefresh();
    }

    @Override
    public void onError(Throwable e) {

    }

    @Override
    public void onNext(GithubEvent event) {
      if (getAdapter() != null) {
        getAdapter().add(event);
      }
    }
  };

  public static EventsListFragment newInstance(String username) {
    Bundle bundle = new Bundle();
    bundle.putString(USERNAME, username);

    EventsListFragment f = new EventsListFragment();
    f.setArguments(bundle);

    return f;
  }

  @Override
  public void onResume() {
    super.onResume();

    getActivity().setTitle(R.string.menu_events);
  }

  @Override
  protected void onResponse(List<GithubEvent> githubEvents, boolean refreshing) {

  }

  private boolean checkEventHandled(GithubEvent event) {
    return event.getType() != null && (event.getType() == EventType.PushEvent)
        || (event.getType()
        == EventType.WatchEvent)
        || (event.getType() == EventType.CreateEvent)
        || (event.getType()
        == EventType.IssueCommentEvent)
        || (event.getType() == EventType.CommitCommentEvent)
        || (event.getType()
        == EventType.IssuesEvent)
        || (event.getType() == EventType.ForkEvent)
        || (event.getType() == EventType.ReleaseEvent)
        || (event.getType() == EventType.PullRequestEvent)
        || (event.getType() == EventType.DeleteEvent);
  }

  @Override
  protected void loadArguments() {
    username = getArguments().getString(USERNAME);
  }

  @Override
  protected void executeRequest() {
    super.executeRequest();
    GetUserEventsClient eventsClient = new GetUserEventsClient(getActivity(), username);
    executeClient(eventsClient);
  }

  private void executeClient(GetUserEventsClient eventsClient) {
    eventsClient.observable()
        .observeOn(AndroidSchedulers.mainThread())
        .flatMap(new Func1<List<GithubEvent>, Observable<GithubEvent>>() {
          @Override
          public Observable<GithubEvent> call(List<GithubEvent> githubEvents) {
            return Observable.from(githubEvents);
          }
        })
        .filter(getFilterNames())
        .subscribe(subscriber);
  }

  @Override
  protected void executePaginatedRequest(int page) {
    super.executePaginatedRequest(page);

    GetUserEventsClient eventsClient = new GetUserEventsClient(getActivity(), username, page);
    executeClient(eventsClient);
  }

  @Override
  protected Octicons.Icon getNoDataIcon() {
    return Octicons.Icon.oct_calendar;
  }

  @Override
  protected int getNoDataText() {
    return R.string.noevents;
  }

  @Override
  public void onItem(GithubEvent item) {
    EventType type = item.getType();
    Gson gson = new Gson();
    if (type == EventType.IssueCommentEvent) {
      String s = gson.toJson(item.payload);
      IssueCommentEventPayload payload = gson.fromJson(s, IssueCommentEventPayload.class);
      Issue issue = payload.issue;
      if (issue != null) {
        startActivity(new UrlsManager(getActivity()).checkUri(Uri.parse(issue.html_url)));
      }
    } else if (type == EventType.PushEvent) {
      String payload = gson.toJson(item.payload);
      PushEventPayload pushEventPayload = gson.fromJson(payload, PushEventPayload.class);
      if (pushEventPayload != null && pushEventPayload.commits != null) {
        if (pushEventPayload.commits.size() == 1) {
          Commit commit = pushEventPayload.commits.get(0);
          startActivity(new UrlsManager(getActivity()).checkUri(Uri.parse(commit.url)));
        } else if (pushEventPayload.commits.size() > 1) {
          showCommitsDialog(pushEventPayload.commits);
        }
      }
    } else if (type == EventType.IssuesEvent) {
      String payload = gson.toJson(item.payload);
      IssueEventPayload issueEventPayload = gson.fromJson(payload, IssueEventPayload.class);
      if (issueEventPayload != null) {
        startActivity(
            new UrlsManager(getActivity()).checkUri(Uri.parse(issueEventPayload.issue.html_url)));
      }
    } else if (type == EventType.PullRequestEvent) {
      String payload = gson.toJson(item.payload);
      PullRequestEventPayload pullRequestEventPayload =
          gson.fromJson(payload, PullRequestEventPayload.class);
      if (pullRequestEventPayload != null) {
        startActivity(new UrlsManager(getActivity()).checkUri(
            Uri.parse(pullRequestEventPayload.pull_request.html_url)));
      }
    } else if (type == EventType.ForkEvent) {
      String payload = gson.toJson(item.payload);
      ForkEventPayload forkEventPayload = gson.fromJson(payload, ForkEventPayload.class);
      if (forkEventPayload != null) {
        String parentRepo = item.repo.name;
        String forkeeRepo = forkEventPayload.forkee.full_name;

        showReposDialogDialog(parentRepo, forkeeRepo);
      }
    } else if (type == EventType.ReleaseEvent) {
      String payload = gson.toJson(item.payload);
      ReleaseEventPayload releaseEventPayload = gson.fromJson(payload, ReleaseEventPayload.class);
      if (releaseEventPayload != null) {
        Intent intent =
            new UrlsManager(getActivity()).checkUri(Uri.parse(releaseEventPayload.release.url));

        if (intent != null) {
          startActivity(intent);
        }
      }
    } else {
      // TODO manage TAGs
      if (item.repo.url != null) {
        startActivity(new UrlsManager(getActivity()).manageRepos(Uri.parse(item.repo.url)));
      }
    }
  }

  private void showCommitsDialog(List<Commit> commits) {
    final CommitsAdapter adapter = new CommitsAdapter(getActivity(), commits);
    MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
    builder.title(R.string.event_select_commit);
    builder.adapter(adapter, new MaterialDialog.ListCallback() {
      @Override
      public void onSelection(MaterialDialog materialDialog, View view, int i,
          CharSequence charSequence) {
        Commit item = adapter.getItem(i);

        startActivity(new UrlsManager(getActivity()).checkUri(Uri.parse(item.url)));
      }
    });
    builder.show();
  }

  private class CommitsAdapter extends ArrayAdapter<Commit> {

    private final LayoutInflater mInflater;

    public CommitsAdapter(Context context, List<Commit> objects) {
      super(context, 0, objects);
      this.mInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View view = mInflater.inflate(R.layout.commit_row, parent, false);

      ViewHolder holder = new ViewHolder(view);

      Commit commit = getItem(position);

      User author = commit.author;

      if (author == null) {
        author = commit.commit.author;
      }

      if (author == null) {
        author = commit.commit.committer;
      }

      if (author != null) {
        if (author.avatar_url != null) {
          ImageLoader.getInstance().displayImage(author.avatar_url, holder.avatar);
        } else if (author.email != null) {
          try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(author.email.getBytes());
            byte messageDigest[] = digest.digest();
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++) {
              hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            }
            String hash = hexString.toString();
            ImageLoader.getInstance()
                .displayImage("http://www.gravatar.com/avatar/" + hash, holder.avatar);
          } catch (NoSuchAlgorithmException e) {
            IconicsDrawable iconDrawable =
                new IconicsDrawable(holder.itemView.getContext(), Octicons.Icon.oct_octoface);
            iconDrawable.color(AttributesUtils.getSecondaryTextColor(holder.itemView.getContext()));
            iconDrawable.sizeDp(36);
            iconDrawable.setAlpha(128);
            holder.avatar.setImageDrawable(iconDrawable);
          }
        } else {
          IconicsDrawable iconDrawable =
              new IconicsDrawable(holder.itemView.getContext(), Octicons.Icon.oct_octoface);
          iconDrawable.color(AttributesUtils.getSecondaryTextColor(holder.itemView.getContext()));
          iconDrawable.sizeDp(36);
          iconDrawable.setAlpha(128);
          holder.avatar.setImageDrawable(iconDrawable);
        }

        if (author.login != null) {
          holder.user.setText(author.login);
        } else if (author.name != null) {
          holder.user.setText(author.name);
        } else if (author.email != null) {
          holder.user.setText(author.email);
        }
      }

      String message = commit.shortMessage();
      if (commit.commit != null && commit.commit.shortMessage() != null) {
        message = commit.commit.shortMessage();
      }

      holder.title.setText(message);

      if (commit.sha != null) {
        holder.sha.setText(commit.shortSha());
      }

      holder.textNums.setText("");

      if (commit.stats != null) {
        String textCommitsStr = null;
        if (commit.stats.additions > 0 && commit.stats.deletions > 0) {
          textCommitsStr = holder.itemView.getContext()
              .getString(R.string.commit_file_add_del, commit.stats.additions,
                  commit.stats.deletions);
          holder.textNums.setVisibility(View.VISIBLE);
        } else if (commit.stats.additions > 0) {
          textCommitsStr = holder.itemView.getContext()
              .getString(R.string.commit_file_add, commit.stats.additions);
          holder.textNums.setVisibility(View.VISIBLE);
        } else if (commit.stats.deletions > 0) {
          textCommitsStr = holder.itemView.getContext()
              .getString(R.string.commit_file_del, commit.stats.deletions);
          holder.textNums.setVisibility(View.VISIBLE);
        } else {
          holder.textNums.setVisibility(View.GONE);
        }

        if (textCommitsStr != null) {
          holder.textNums.setText(Html.fromHtml(textCommitsStr));
        }
      } else {
        holder.textNums.setVisibility(View.GONE);
      }

      if (commit.files != null && commit.files.size() > 0) {
        holder.numFiles.setVisibility(View.VISIBLE);
        holder.numFiles.setText(
            holder.itemView.getContext().getString(R.string.num_of_files, commit.files.size()));
      } else {
        holder.numFiles.setVisibility(View.GONE);
      }

      return view;
    }

    public class ViewHolder {

      private final TextView title;
      private final TextView user;
      private final TextView sha;
      private final TextView textNums;
      private final TextView numFiles;
      private final ImageView avatar;
      private View itemView;

      public ViewHolder(final View itemView) {
        this.itemView = itemView;
        title = (TextView) itemView.findViewById(R.id.title);
        user = (TextView) itemView.findViewById(R.id.user);
        sha = (TextView) itemView.findViewById(R.id.sha);
        textNums = (TextView) itemView.findViewById(R.id.textNums);
        numFiles = (TextView) itemView.findViewById(R.id.numFiles);
        avatar = (ImageView) itemView.findViewById(R.id.avatarAuthor);
      }
    }
  }

  private void showReposDialogDialog(final String... repos) {

    MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
    builder.title(R.string.event_select_repository);
    builder.items(repos);
    builder.alwaysCallSingleChoiceCallback();
    builder.itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
      @Override
      public boolean onSelection(MaterialDialog materialDialog, View view, int i,
          CharSequence charSequence) {
        String repoSelected = repos[i];
        String[] split = repoSelected.split("/");
        RepoInfo repoInfo = new RepoInfo();
        repoInfo.owner = split[0];
        repoInfo.name = split[1];

        Intent intent = RepoDetailActivity.createLauncherIntent(getActivity(), repoInfo);
        startActivity(intent);
        return true;
      }
    });

    builder.show();
  }
}
