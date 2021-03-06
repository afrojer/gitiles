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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gitiles.CommitSoyData.KeySet;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

public class LogSoyData {
  private final HttpServletRequest req;
  private final GitilesView view;

  public LogSoyData(HttpServletRequest req, GitilesView view) {
    this.req = req;
    this.view = view;
  }

  public Map<String, Object> toSoyData(RevWalk walk, int limit, @Nullable String revision,
      @Nullable ObjectId start) throws IOException {
    Map<String, Object> data = Maps.newHashMapWithExpectedSize(3);

    Paginator paginator = new Paginator(walk, limit, start);
    List<Map<String, Object>> entries = Lists.newArrayListWithCapacity(limit);
    for (RevCommit c : paginator) {
      entries.add(new CommitSoyData().toSoyData(req, c, KeySet.SHORTLOG));
    }

    data.put("entries", entries);
    ObjectId next = paginator.getNextStart();
    if (next != null) {
      data.put("nextUrl", copyAndCanonicalize(view, revision)
          .replaceParam(LogServlet.START_PARAM, next.name())
          .toUrl());
    }
    ObjectId prev = paginator.getPreviousStart();
    if (prev != null) {
      GitilesView.Builder prevView = copyAndCanonicalize(view, revision);
      if (!prevView.getRevision().getId().equals(prev)) {
        prevView.replaceParam(LogServlet.START_PARAM, prev.name());
      }
      data.put("previousUrl", prevView.toUrl());
    }
    return data;
  }

  private static GitilesView.Builder copyAndCanonicalize(GitilesView view, String revision) {
    // Canonicalize the view by using full SHAs.
    GitilesView.Builder copy = GitilesView.log().copyFrom(view);
    if (view.getRevision() != Revision.NULL) {
      copy.setRevision(view.getRevision());
    } else {
      copy.setRevision(Revision.named(revision));
    }
    if (view.getOldRevision() != Revision.NULL) {
      copy.setOldRevision(view.getOldRevision());
    }
    return copy;
  }
}
