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

import static com.google.gitiles.FakeHttpServletRequest.SERVLET_PATH;
import static com.google.gitiles.TestGitilesUrls.HOST_NAME;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_PERMANENTLY;

import javax.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.net.HttpHeaders;

/** Tests for gitweb redirector. */
public class GitwebRedirectFilterTest extends TestCase {
  private TestRepository<DfsRepository> repo;
  private GitilesServlet servlet;

  @Override
  protected void setUp() throws Exception {
    repo = new TestRepository<DfsRepository>(
        new InMemoryRepository(new DfsRepositoryDescription("test")));
    servlet = TestGitilesServlet.create(repo);
  }

  private void assertRedirectsTo(String expectedLocation, HttpServletRequest req) throws Exception {
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);
    assertEquals(SC_MOVED_PERMANENTLY, res.getStatus());
    assertEquals(expectedLocation, res.getHeader(HttpHeaders.LOCATION));
  }

  private void assertGone(HttpServletRequest req) throws Exception {
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.service(req, res);
    assertEquals(SC_GONE, res.getStatus());
  }

  private static FakeHttpServletRequest newRequest(String qs) {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/");
    req.setQueryString(qs);
    return req;
  }

  public void testHostIndex() throws Exception {
    assertRedirectsTo(
        GitilesView.hostIndex()
            .setHostName(HOST_NAME)
            .setServletPath(SERVLET_PATH)
            .toUrl(),
        newRequest("a=project_index"));
  }

  public void testRepositoryIndex() throws Exception {
    assertGone(newRequest("a=summary"));
    assertRedirectsTo(
        GitilesView.repositoryIndex()
            .setHostName(HOST_NAME)
            .setServletPath(SERVLET_PATH)
            .setRepositoryName("test")
            .toUrl(),
        newRequest("a=summary;p=test"));
  }

  public void testShow() throws Exception {
    assertGone(newRequest("a=commit"));
    assertGone(newRequest("a=commit;p=test"));
    RevCommit commit = repo.branch("refs/heads/master").commit().create();
    assertRedirectsTo(
        GitilesView.revision()
            .setHostName(HOST_NAME)
            .setServletPath(SERVLET_PATH)
            .setRepositoryName("test")
            .setRevision(commit)
            .toUrl(),
        newRequest("a=commit;p=test&h=" + ObjectId.toString(commit)));
  }

  public void testNoStripDotGit() throws Exception {
    assertRedirectsTo(
        GitilesView.repositoryIndex()
            .setHostName(HOST_NAME)
            .setServletPath(SERVLET_PATH)
            .setRepositoryName("test.git")
            .toUrl(),
        newRequest("a=summary;p=test.git"));
    assertRedirectsTo(
        GitilesView.repositoryIndex()
            .setHostName(HOST_NAME)
            .setServletPath(SERVLET_PATH)
            .setRepositoryName("test")
            .toUrl(),
        newRequest("a=summary;p=test"));
  }

  public void testStripDotGit() throws Exception {
    servlet = TestGitilesServlet.create(repo, new GitwebRedirectFilter(true));
    assertRedirectsTo(
        GitilesView.repositoryIndex()
            .setHostName(HOST_NAME)
            .setServletPath(SERVLET_PATH)
            .setRepositoryName("test")
            .toUrl(),
        newRequest("a=summary;p=test.git"));
    assertRedirectsTo(
        GitilesView.repositoryIndex()
            .setHostName(HOST_NAME)
            .setServletPath(SERVLET_PATH)
            .setRepositoryName("test")
            .toUrl(),
        newRequest("a=summary;p=test"));
  }
}
