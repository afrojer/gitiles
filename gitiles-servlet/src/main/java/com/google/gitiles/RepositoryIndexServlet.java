// Copyright 2012 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves the index page for a repository, if accessed directly by a browser. */
public class RepositoryIndexServlet extends BaseServlet {
  private static final long serialVersionUID = 1L;

  static final int REF_LIMIT = 10;
  private static final int LOG_LIMIT = 20;

  private final GitilesAccess.Factory accessFactory;
  private final TimeCache timeCache;

  public RepositoryIndexServlet(Renderer renderer, GitilesAccess.Factory accessFactory,
      TimeCache timeCache) {
    super(renderer);
    this.accessFactory = checkNotNull(accessFactory, "accessFactory");
    this.timeCache = checkNotNull(timeCache, "timeCache");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    renderHtml(req, res, "gitiles.repositoryIndex", buildData(req));
  }

  @VisibleForTesting
  Map<String, ?> buildData(HttpServletRequest req) throws IOException {
    GitilesView view = ViewFilter.getView(req);
    Repository repo = ServletUtils.getRepository(req);
    RepositoryDescription desc = accessFactory.forRequest(req).getRepositoryDescription();
    RevWalk walk = new RevWalk(repo);
    List<Map<String, Object>> tags;
    Map<String, Object> data;
    try {
      tags = RefServlet.getTagsSoyData(req, timeCache, walk, REF_LIMIT);
      ObjectId headId = repo.resolve(Constants.HEAD);
      if (headId != null) {
        RevObject head = walk.parseAny(headId);
        if (head.getType() == Constants.OBJ_COMMIT) {
          walk.reset();
          walk.markStart((RevCommit) head);
          data =
              new LogSoyData(req, view).toSoyData(walk, LOG_LIMIT, "HEAD", null);
        } else {
          // TODO(dborowitz): Handle non-commit or missing HEAD?
          data = Maps.newHashMapWithExpectedSize(6);
        }
      } else {
        data = Maps.newHashMapWithExpectedSize(6);
      }
    } finally {
      walk.release();
    }
    if (!data.containsKey("entries")) {
      data.put("entries", ImmutableList.of());
    }
    List<Map<String, Object>> branches = RefServlet.getBranchesSoyData(req, REF_LIMIT);

    data.put("cloneUrl", desc.cloneUrl);
    data.put("mirroredFromUrl", Strings.nullToEmpty(desc.mirroredFromUrl));
    data.put("description", Strings.nullToEmpty(desc.description));
    data.put("branches", trim(branches));
    if (branches.size() > REF_LIMIT) {
      data.put("moreBranchesUrl", GitilesView.refs().copyFrom(view).toUrl());
    }
    data.put("tags", trim(tags));
    if (tags.size() > REF_LIMIT) {
      data.put("moreTagsUrl", GitilesView.refs().copyFrom(view).toUrl());
    }
    return data;
  }

  private static <T> List<T> trim(List<T> list) {
    return list.size() > REF_LIMIT ? list.subList(0, REF_LIMIT) : list;
  }
}
