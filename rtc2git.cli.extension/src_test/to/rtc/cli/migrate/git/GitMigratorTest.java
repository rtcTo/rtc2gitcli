/**
 * File Name: GitMigratorTest.java
 * 
 * Copyright (c) 2015 BISON Schweiz AG, All Rights Reserved.
 */

package to.rtc.cli.migrate.git;

import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests the {@link GitMigrator} implementation.
 *
 * @author patrick.reinhart
 */
public class GitMigratorTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private GitMigrator migrator;
  private Git git;

  @Before
  public void setUo() {
    migrator = new GitMigrator();
  }

  @After
  public void tearDown() {
    migrator.close();
    if (git != null) {
      git.close();
    }
  }

  /**
   * Test method for {@link to.rtc.cli.migrate.git.GitMigrator#close()}.
   */
  @Test
  public void testClose() {
    migrator.close();
  }

  /**
   * Test method for {@link to.rtc.cli.migrate.git.GitMigrator#init(java.nio.file.Path, java.util.Properties)}.
   */
  @Test
  public void testInit_noGitRepoAvailableNoGitIgnore() throws Exception {
    Path gitRoot = tempFolder.getRoot().toPath();
    Properties props = new Properties();
    props.setProperty("user.email", "john.doe@somewhere.com");
    props.setProperty("user.name", "John Doe");

    migrator.init(gitRoot, props);

    checkGit(gitRoot, "John Doe", "john.doe@somewhere.com", GitMigrator.ROOT_IGNORED_ENTRIES);
  }

  /**
   * Test method for {@link to.rtc.cli.migrate.git.GitMigrator#init(java.nio.file.Path, java.util.Properties)}.
   */
  @Test
  public void testInit_noGitRepoAvailableWithGitIgnore() throws Exception {
    Path gitRoot = tempFolder.getRoot().toPath();
    write(gitRoot.resolve(".gitignore"), Arrays.asList("/.jazz5", "/bin/", "/.jazzShed"), StandardCharsets.UTF_8);
    Properties props = new Properties();

    migrator.init(gitRoot, props);

    checkGit(gitRoot, "RTC 2 git", "rtc2git@rtc.to", Arrays.asList("/.jazz5", "/bin/", "/.jazzShed", "/.metadata"));
  }

  /**
   * Test method for {@link to.rtc.cli.migrate.git.GitMigrator#init(java.nio.file.Path, java.util.Properties)}.
   */
  @Test
  public void testInit_GitRepoAvailable() throws Exception {
    Path gitRoot = tempFolder.getRoot().toPath();
    Properties props = new Properties();
    git = Git.init().setDirectory(gitRoot.toFile()).call();

    migrator.init(gitRoot, props);

    checkGit(gitRoot, "RTC 2 git", "rtc2git@rtc.to", GitMigrator.ROOT_IGNORED_ENTRIES);
  }

  private void checkGit(Path gitRoot, String userName, String userEmail, List<String> ignoreEntries) throws Exception {
    assertEquals(ignoreEntries, readAllLines(gitRoot.resolve(".gitignore"), StandardCharsets.UTF_8));
    git = Git.open(tempFolder.getRoot());
    Status status = git.status().call();
    assertTrue(status.getUncommittedChanges().isEmpty());
    assertTrue(status.getUntracked().isEmpty());
    Iterator<RevCommit> log = git.log().call().iterator();
    RevCommit revCommit = log.next();
    assertEquals(userEmail, revCommit.getAuthorIdent().getEmailAddress());
    assertEquals(userName, revCommit.getAuthorIdent().getName());
    assertEquals("initial commit", revCommit.getFullMessage());
    assertFalse(log.hasNext());
  }
}