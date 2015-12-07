# rtc2gitcli
rtc2git written as scm cli plugin

# rtc2gitcli
A tool made for migrating code from an existing [RTC] (https://jazz.net/products/rational-team-concert/) SCM repository into a Git repository
It is implemented as scm cli plugin.

## Prerequirements
- **[SCM Tools](https://jazz.net/downloads/rational-team-concert/releases/5.0.1?p=allDownloads)** from IBM. To avoid an account creation on jazz.net site, you could use [bugmenot](http://bugmenot.com/) (see also wiki page [configure RTC CLI] (https://github.com/rtcTo/rtc2git/wiki/configure-RTC-CLI))

## Usage
- create an RTC workspace with flow target and components as wanted --> SOURCE_WORKSPACE
- create an RTC workspace with SOURCE_WORKPSACE as flow target --> TARGET_WORKSPACE
- open shell or cmd
- step into the target directory
- scm load -r <uri> -u <username> -P password TARGET_WORKSPACE
- scm migrate-to-git [options] SOURCE_WORKSPACE TARGET_WORKSPACE (options are -r uri, -u username, -P password)


## How does it work?
1. It initalizes an empty git repository and clones it
2. In this repository, it loads TARGET_WORKSPACE rtc workspace
3. Every change set is accepted
4. If there is a baseline on the change set, a tag is created on git
5. The change set is committed to git


## Contribute
We welcome any feedback! :)

Feel free to report and/or fix [issues](https://github.com/rtcTo/rtc2gitcli/issues) or create new pull requests

## Wiki
For more details [visit our wiki] (https://github.com/rtcTo/rtc2gitcli/wiki)
