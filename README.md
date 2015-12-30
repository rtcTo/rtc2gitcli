# rtc2gitcli
A tool made for migrating code from an existing [RTC] (https://jazz.net/products/rational-team-concert/) SCM repository into a Git repository.
Inspired by [rtc2git](https://github.com/rtcTo/rtc2git), but written in Java and hopefully faster. It is implemented as scm cli plugin.


## Prerequirements
- **[SCM Tools](https://jazz.net/downloads/rational-team-concert/releases/5.0.1?p=allDownloads)** from IBM. To avoid an account creation on jazz.net site, you could use [bugmenot](http://bugmenot.com/) (see also wiki page [configure RTC CLI] (https://github.com/rtcTo/rtc2git/wiki/configure-RTC-CLI))
- Eclipse configured with scm tools as target platform (see wiki [configure scm tools target platform](https://github.com/rtcTo/rtc2gitcli/wiki/configure-target-platform))

## Usage
- create an source RTC workspace with flow target and components as wanted --> *SOURCE_WORKSPACE*
- create an target RTC workspace with *SOURCE_WORKPSACE* as flow target --> *TARGET_WORKSPACE*
- check comments at [configure rtc workspaces](https://github.com/rtcTo/rtc2gitcli/wiki/configure-rtc-workspaces)
- open shell or cmd
- step into the target directory
- load the initial target workspace:

```
scm load -r <uri> -u <username> -P <password> <TARGET_WORKSPACE>
```

- execute the actual migration:

```
scm migrate-to-git -r <uri> -u <username> -P <password> -m <migration.properties> <SOURCE_WORKSPACE> <TARGET_WORKSPACE>
```

## How does it work?
1. It initalizes an empty git repository and clones it
2. In this repository, it loads *TARGET_WORKSPACE* rtc workspace
3. Every change set is accepted
4. If there is a baseline on the change set, a tag is created on git
5. The change set is committed to git


### Eclipse requirements
In order to have a common coding style across multiple versions of Eclipse import the `eclipse-rtccli-format-settings.xml` as your first
step. Then unpack a SCM Tools as downloaded into the Eclipse workspace directory. As soon you have done this step you are able to import the actual 
`rtc2git.cli.extension` project. When you got compile errors, make sure to
select *rtc2git* as your default Target Platform.


## Wiki
For more details [visit our wiki] (https://github.com/rtcTo/rtc2gitcli/wiki)

## Links for JGit
- [api docs] (http://download.eclipse.org/jgit/docs/jgit-3.3.0.201403021825-r/apidocs/?d)
- [jgit cookbook] (https://github.com/centic9/jgit-cookbook)
- [User Guide] (http://wiki.eclipse.org/JGit/User_Guide)

## Contribute & Feedback
Feel free to report and/or fix [issues](https://github.com/rtcTo/rtc2gitcli/issues) or create new pull requests
