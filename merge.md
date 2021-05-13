# Merge History

This repository was merged from 4 original OpenSHA repositories: opensha-commons, opensha-core, opensha-ucerf3, and opensha-apps.

This file documents the steps that were taken to merge them with history, and will be removed when the merge is complete and the projects are stable.

```
git remote add -f commons git@github.com:opensha/opensha-commons.git
git remote add -f core git@github.com:opensha/opensha-core.git
git remote add -f ucerf3 git@github.com:opensha/opensha-ucerf3.git
git remote add -f apps git@github.com:opensha/opensha-apps.git

git merge --allow-unrelated-histories commons/2021_05-repo-merge-base
git merge --allow-unrelated-histories core/2021_05-repo-merge-base
git merge --allow-unrelated-histories ucerf3/2021_05-repo-merge-base
git merge --allow-unrelated-histories apps/2021_05-repo-merge-base

git remote rm commons
git remote rm core
git remote rm ucerf3
git remote rm apps
```

Here are the steps that were taken to update the directory structure to the [Maven Standard Directory Layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html). This was done using [this tool](https://gist.github.com/emiller/6769886):

```
./git-rewrite-history src/org=src/main/java/org
./git-rewrite-history src/scratch=src/main/java/scratch
./git-rewrite-history src/resources=src/main/resources
./git-rewrite-history test/resources=src/test/resources
./git-rewrite-history test=src/test/java
git push -f
```

