# Settings

`clojure-lsp` settings are picked up only on server start and can be configured in 4 ways:

- [Project configuration](#project)
- [Global configuration](#global)
- [LSP InitializationOptions](#initialization-options)
- [Classpath config paths](#classpath-config-paths)

### Project

`clojure-lsp` will look for project specific settings in a file called `.lsp/config.edn`. It will search from your project root folder up the directory structure so you can have multiple projects share the same settings.

Example:
```clojure
{:cljfmt {:indents {#re ".*" ns [[:inner 0] [:inner 1]]}}
 :auto-add-ns-to-new-files? false}
```

---
### Global

For global settings which should work for all the projects using `clojure-lsp`, you just need to add the same configs to `~/.lsp/config.edn` or `$XDG_CONFIG_HOME/clojure-lsp/config.edn`.

For an example of a global `config.edn`, check [here](https://github.com/ericdallo/dotfiles/blob/master/.lsp/config.edn).

---
### InitializationOptions

This is specific for a client, which it sends to `clojure-lsp` on startup, check [LSP spec for more information](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#initialize). This is useful if you are changing a default for a client/editor that will affect all users of that editor.

This is an [example how Emacs `lsp-mode`](https://github.com/emacs-lsp/lsp-mode/blob/master/clients/lsp-clojure.el#L205) pass custom information.

Another example, for neovim users, using [coc.nvim](https://github.com/neoclide/coc.nvim) and [coc-clojure](https://github.com/NoahTheDuke/coc-clojure) can be found [here](https://github.com/dharrigan/vimconfig/blob/master/coc-settings.json)

If you are using a client which defines InitializationOptions as a json object, you can use json types instead:

- keyword -> string or colon-prefixed string (`"incremental"` or `":incremental"`)
- map -> object (`{"unused-public-ns": {"level": "info"}}`)
- set -> array (`["src" "test"]`)
- vector -> array (`["src" "test"]`)

### Classpath config paths

When starting the server, clojure-lsp will search for a specific path `clojure-lsp.exports/<group-id>/<artifact-id>/config.edn` for extra configurations in the classpath, only if you set the setting `:classpath-config-paths` containing that `group/artifact`. Example:

Lib `my-org/my-lib` could have a clojure-lsp config edn that makes sense for the usages of that lib, making available on the classpath:

`my-lib/resources/clojure-lsp.exports/my-org/my-lib/config.edn`
```clojure
{:cljfmt {:indents {foo [[:block 0]]}}}
```

and then if your project use/has the `my-org/my-lib` inside your classpath, you could add the setting

`your-project/.lsp/config.edn`
```clojure
{:classpath-config-paths ["my-org/my-lib"]}
```

And then clojure-lsp will merge the configuration from the lib with the other configurations from your project.

This is useful if you have some rule to apply to clojure-lsp for multiple projects, mostly using via API for linting for example, and want to move the common configuration to some place instead of adding to each project that needs that.

## All settings

You can find all settings and its default values [here](https://github.com/clojure-lsp/clojure-lsp/blob/master/docs/all-available-settings.edn) and below the docs for each one:

| name                                    | description                                                                                                                                                                                                                                                                                                                                                                                                          | default                                                                                       |
|-----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `:clean`                                | Settings related to `clean-ns` refactoring. Check `clean` settings below.                                                                                                                                                                                                                                                                                                                                            |                                                                                               |
| `additional-snippets`                   | Additional user snippets to be available during completing, check the snippets section below                                                                                                                                                                                                                                                                                                                         | `[]`                                                                                          |
| `api exit-on-errors?`                   | Whether to exit the clojure-lsp process during api/cli call if any error is found, like classpath scan failure                                                                                                                                                                                                                                                                                                       | `true`                                                                                        |
| `auto-add-ns-to-new-files?`             | Whether to automatically add the `ns` form in new blank files.                                                                                                                                                                                                                                                                                                                                                       | `true`                                                                                        |
| `cache-path`                            | Where to store the project's analysis cache, used to speed up next `clojure-lsp` startup. A path relative to project root or an absolute path.                                                                                                                                                                                                                                                                       | `.lsp/.cache`                                                                                 |
| `classpath-config-paths`                | List of extra configurations to load from classpath, for more info, check [Classpath config paths](#classpath-config-paths) section.                                                                                                                                                                                                                                                                                 | `[]`                                                                                          |
| `cljfmt-config-path`                    | Where to find cljfmt configuration for formatting. A path relative to project root or an absolute path. Use `#re` for regex inside the cljfmt configuration file.                                                                                                                                                                                                                                                    | `.cljfmt.edn`                                                                                 |
| `cljfmt`                                | If no `:cljfmt-config-path` is provided, used this for formatting, json encoded configuration for [cljfmt](https://github.com/weavejester/cljfmt)                                                                                                                                                                                                                                                                    | `{}`                                                                                          |
| `code-lens` `segregate-test-references` | Segregate main references from test references with option to disable                                                                                                                                                                                                                                                                                                                                                | `true`                                                                                        |
| `copy-kondo-configs?`                   | Whether to copy clj-kondo hooks configs exported by libs on classpath during startup lint.                                                                                                                                                                                                                                                                                                                           | `true`                                                                                        |
| `dependency-scheme`                     | How the dependencies should be linked, `jar` will make urls compatible with java's JarURLConnection. You can have the client make an lsp extension request of `clojure/dependencyContents` with the jar uri and the server will return the jar entry's contents. [Similar to java clients](https://github.com/redhat-developer/vscode-java/blob/a24945453092e1c39267eac9367c759a6c7b0497/src/extension.ts#L290-L298) | `zip`                                                                                         |
| `document-formatting?`                  | if true or not present, document formatting is provided.                                                                                                                                                                                                                                                                                                                                                             | `true`                                                                                        |
| `document-range-formatting?`            | if true or not present, document range formatting is provided.                                                                                                                                                                                                                                                                                                                                                       | `true`                                                                                        |
| `hover` `arity-on-same-line?`           | Whether to keep the arity on the same line of the function on hover, useful for Emacs users.                                                                                                                                                                                                                                                                                                                         | `false`                                                                                       |
| `hover` `clojuredocs`                   | Whether to get clojuredocs information on hover, the clojuredocs content is cached.                                                                                                                                                                                                                                                                                                                                  | `true`                                                                                        |
| `hover` `hide-file-location?`           | Whether to show the full filename and path on hover.                                                                                                                                                                                                                                                                                                                                                                 | `false`                                                                                       |
| `ignore-classpath-directories`          | will not consider clojure files within the directories specified by your classpath. This is needed, for instance, if your build puts artifacts into `resources` or `target` that you want lsp to ignore.                                                                                                                                                                                                             | `false`                                                                                       |
| `java` `download-jdk-source?`           | Whether to download JDK source from `:java :jdk-source-download-uri` and cache after startup for JDK classes java support.                                                                                                                                                                                                                                                                                           | `false`                                                                                       |
| `java` `jdk-source-uri`                 | URI containing the JDK source to be used. If `download-jdk-source?` is enabled and URI is *NOT* `file://` then download the source. Check `https://github.com/clojure-lsp/jdk-source`for more sources.                                                                                                                                                                                                               | `https://raw.githubusercontent.com/clojure-lsp/jdk-source/main/openjdk-19/reduced/source.zip` |
| `keep-parens-when-threading?`           | Whether to keep parenthesis when threading single arity functions.                                                                                                                                                                                                                                                                                                                                                   | `false`                                                                                       |
| `lint-project-files-after-startup?`     | Whether to async lint all project only files after startup to make features like [List project errors](https://emacs-lsp.github.io/lsp-mode/page/main-features/#project-errors-on-modeline) work.                                                                                                                                                                                                                    | `true`                                                                                        |
| `linters`                               | clojure-lsp custom linters, check the diagnostics settings section below                                                                                                                                                                                                                                                                                                                                             |                                                                                               |
| `log-path`                              | A absolute path to a file where clojure-lsp should log.                                                                                                                                                                                                                                                                                                                                                              | A JVM tmp path, usually `/tmp/clojure-lsp.*.out`                                              |
| `notify-references-on-file-change`      | Whether to update diagnostics of the changed references when editing files, avoiding outdated diagnostics in other files.                                                                                                                                                                                                                                                                                            | `true`                                                                                       |
| `project-specs`                         | A vector of a map with `project-path` and `classpath-cmd`, defining how `clojure-lsp` should find your project classpath. the `project-path` should be a file and the `classpath-cmd` the command to run to get the classpath                                                                                                                                                                                        | Check `Classpath scan` section below                                                          |
| `semantic-tokens?`                      | Whether to enable LSP semantic tokens server support for syntax highlighting.                                                                                                                                                                                                                                                                                                                                        | `true`                                                                                        |
| `source-aliases`                        | Used for `deps.edn` or `project.clj` projects, the aliases which clojure-lsp should get the source-paths besides the root level `:paths` and `:extra-paths`. Check the `source-aliases discovery` section below.                                                                                                                                                                                                     | `#{:dev :test}`                                                                               |
| `source-paths`                          | project-local directories to look for clj/cljc/cljs files, if using `deps.edn`, `project.clj` or `bb.edn`, use `:source-aliases` instead.                                                                                                                                                                                                                                                                            | `#{"src" "test"}`                                                                             |
| `stubs`                                 | Stub generation related settings, check [stub generation](#stub-generation) section.                                                                                                                                                                                                                                                                                                                                 |                                                                                               |
| `text-document-sync-kind`               | The sync kind during document changes, if client should send whole buffer or just related changes. Should be `:full` or `:incremental`                                                                                                                                                                                                                                                                               | `:full`                                                                                       |
| `use-metadata-for-privacy?`             | Whether to use `^:private` metadata for refactorings instead of `defn-`                                                                                                                                                                                                                                                                                                                                              | `false`                                                                                       |
| `use-source-paths-from-classpath`       | Whether to check source-paths from classpath or manually discover                                                                                                                                                                                                                                                                                                                                                    | `true`                                                                                        |

### Classpath scan

clojure-lsp needs to analyze the whole project and its dependencies to understand your code for most features, during the startup clojure-lsp will try to find the classpath of your project to pass to clj-kondo later.

You can configure how clojure-lsp should find the classpath with the `project-specs` setting, but keep in mind that usually the [default](https://github.com/clojure-lsp/clojure-lsp/blob/master/lib/src/clojure_lsp/classpath.clj#L108-L123) is enough, it will also consider the `:source-aliases` setting if any to find the classpath using those aliases.

Supported project types at the moment are:

- `leiningen`: If a `project.clj` is found at the project root, clojure-lsp will run `lein classpath` with `:source-aliases` specified if any.
- `deps`: If a `deps.edn` is found at the project root, clojure-lsp will run `clojure -Spath` with `:source-aliases` specified if any.
- `boot`: If a `build.boot` is found at the project root, clojure-lsp will run `boot show --fake-classpath`.
- `shadow-cljs`: If a `shadow-cljs.edn` is found at the project root, clojure-lsp will run `npx shadow-cljs classpath`.
- `babashka`: If a `bb.edn` is found at the project root, clojure-lsp will run `bb print-deps --format classpath`.

Note that it's possible to have more and one project type at the same time e.g. deps + babashka, clojure-lsp will merge the classpath and everything should works fine.

Make sure to have these programs available on the `PATH` environment variable used by your editor, otherwise clojure-lsp will warn about a classpath scan fail, causing a lot of features to not work properly.

Alternatively, you can configure the `project-specs` specific for your project, for example:

`.lsp/config.edn`
```clojure
{:project-specs [{:project-path "deps.edn"
                  :classpath-cmd ["clojure" "-A:my-custom-alias" "-Spath"]}]}
```

Note that clojure-lsp will make this scan to save the cache when:

- The project has no cache (`.lsp/.cache`)
- The project deps file (`project.clj` for example) changed.
- The clj-kondo config has changed.

### Diagnostics (linter)

Default: Check `:linters` in [all-available-settings.edn](https://github.com/clojure-lsp/clojure-lsp/blob/master/docs/all-available-settings.edn).

#### clj-kondo

`clojure-lsp` uses [clj-kondo](https://github.com/clj-kondo/clj-kondo) under the hood to lint the code and retrieve the analysis to
make most of features work, you don't have to install clj-kondo to
make it work.

`clojure-lsp` will use a specific clj-kondo version that can be retrieved via `clojure-lsp --version`, but make sure you have it
properly configured in your `.clj-kondo/config.edn` file.

It has the possible key/values:

- `:clj-kondo`
  - `:level` with available values: `:off`, `:on` with default value of `:on`
  - `:report-duplicates` which will show all linters of the same symbol instead of showing only the first spot. Available values: `true`, `false` with default value of `true`
  - `ns-exclude-regex` which will exclude the diagnostics/findings for namespaces that match this regex.
  - `async-custom-lint?`, whether to async lint custom clojure-lsp linters like unused-public-var, improves UI feedback for huge clojure buffers. Experimental, default `true`.

Example:

`.lsp/config.edn`
```clojure
{:linters {:clj-kondo {:level :on
                       :report-duplicates true
                       :ns-exclude-regex "some-ns.*"}}}
```

<details>
<summary>Note for vim users</summary>

If you are a (neo)vim user and have [ale](https://github.com/dense-analysis/ale) installed as a plugin, you
**should not** have this configured as a linter `let g:ale_linters = {'clojure': ['clj-kondo']}` in your vimrc. Having this
linter enabled via `ale` will only conflict with the built-in clj-kondo bundled with clojure-lsp.

</details>

Also, clojure-lsp by default pass `copy-configs` flag as `true` to clj-kondo to copy configurations exported from libs on classpath, to disable this behavior, set `copy-kondo-configs?` setting to false.

For more information about all clj-kondo available configurations,
check the [clj-kondo configuration section](https://github.com/clj-kondo/clj-kondo/blob/master/doc/config.md)

##### Custom clj-kondo linters

Clojure-lsp register custom linters in clj-kondo, for specifically those linters, configurations should be done on clj-kondo config files, e.g. (`<project>/.clj-kondo/config.edn`), below are the custom linters used:

###### clojure-lsp/unused-public-var

A custom linter that reports public functions/vars not used over the project.

It has the possible key/values:

- `:level` with available values: `:info`, `:warning`, `:error` or `:off` with default value of `:info`.
- `:exclude` a whole namespace with `#{my-ns}` or a specific var `#{my-ns/foo}`.
- `:exclude-when-defined-by` excludes this linter when your var is defined by a macro for example, like `#{my-ns/deftest}`.

Example:

`.clj-kondo/config.edn`
```clojure
{:linters {:clojure-lsp/unused-public-var {:level :warning
                                           :exclude #{my-ns/foo
                                                      my-ns/bar
                                                      other-ns
                                                      my-func}
                                           :exclude-regex #{"my-integration-tests.*"}
                                           :exclude-when-defined-by #{my-ns/defflow}
                                           :exclude-when-defined-by-regex #{"my.custom/macro-.*"}}}}
```

#### Disable linter

It's not recommended to disable the linter as it provides helpful smart checks/suggestions for your code, even so it's possible via the following config:

`.lsp/config.edn`
```clojure
{:linters {:clj-kondo {:level :off}}}
```

For information on how to troubleshoot the linter, check the [troubleshooting section](https://clojure-lsp.io/troubleshooting/)

### Source paths discovery

Some features require know the available source paths of your project, where your code lives, clojure-lsp has some settings for that.

- When `:use-source-paths-from-classpath` setting is enabled (which is true by default), clojure-lsp will get source-paths from the classpath, excluding files that are jar and not under project-root, this usually works for most cases, if not, check `:source-aliases` or `:source-paths` settings.

- If `:use-source-paths-from-classpath` is manually disabled, clojure-lsp will do the following:
  - If your project is a `lein` project, clojure-lsp will scan the `project.clj` file for `:source-paths`, `:test-paths` and the optional `source-paths` from the specified `:source-aliases` setting (default `#{:dev :test}`), unless you specified `:source-paths` setting manually.
  - If your project is a `deps.edn`, clojure-lsp will scan the `deps.edn` file for `:paths`, `:extra-paths`, `:local/root` on `:deps`, `:extra-deps` and the `paths`, `extra-paths`, `:local/root` in `:deps` and `:extra-deps` from the specified `:source-aliases` setting (default `#{:dev :test}`), unless you specified `:source-paths` setting manually.
  - If your project is not a `deps.edn` or `lein` project, a `boot` project for example, clojure-lsp will use only the `:source-paths` setting (default `#{"src" "test"}`) which should point to the folders containing your clojure code.

### Clean

Default: Check `:clean` in [all-available-settings.edn](https://github.com/clojure-lsp/clojure-lsp/blob/master/docs/all-available-settings.edn).

#### after-ns-refactor

Whether to call clean-ns on the namespace after applying any refactor to it like adding missing require/imports/refers.

#### ns-inner-blocks-indentation

How to indent ns children forms like require,import.

##### `next-line`

Keep first child on the next line of `:require`/`:import` and next children following the same indentation. Recommended by [how-to-ns](https://stuartsierra.com/2016/08/27/how-to-ns) guide too.
Check `;; better` on [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide#line-break-ns-declaration).

##### `same-line`

Keep first child on the same line of `:require`/`:import` and next children following the same indentation.
Check `;; good` on [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide#line-break-ns-declaration).

##### `keep`

Don't change indentation at all, keeping the one user selected first.

#### sort

##### `ns`

Whether to enable sort of `ns` children like require, import forms following [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide#comprehensive-ns-declaration).

##### `require`

Whether to enable sort of `:require` form. `true` to sort according to the Clojure Style Guide, `:lexicographically` to do a lexicographic sort that places unwrapped namespaces last.

##### `import`

Whether to enable sort of `:import` form.

##### `refer`

Whether to enable sort of `:refer` form.

- `:max-line-length`: the max refers to keep at same line before breaking the line. Default `80`.

### Snippets

Besides the **19** built-in snippets, it's possible to configure custom additional snippets via `:additional-snippets` setting:

- `:name` the name to use while completing to reach that snippet.
- `:detail` Custom text to show along with the completion name.
- `:snippet` The body of the snippet, besides any text it can contains:
    - `$1`, `$2`, ... as the tabstops representing each place where user may change the content.
    - `$0` as the last tabstop.
    - `$current-form` to replace the current form in the snippet.

Example:

```clojure
{:additional-snippets [{:name "wrap-let-sexpr"
                        :detail "Wrap current sexpr in let"
                        :snippet "(let [$1] $0$current-form)"}]}
```

when completion is called on the code below with the cursor as `|`
```clojure
wrap|(+ 1 2)
```

It should return a completion item that after applied should result in:

```clojure
(let [|] (+ 1 2))
```

### Java support

At the moment, clojure-lsp supports find definition of java classes only, methods, completion and other features are not supported yet.

When a find-definition of a java class is triggered, clojure-lsp checks the following:

- If the java source(`.java`) is available on classpath, clojure-lsp just return that file location.
- If the java source is not available, only the `.class`, clojure-lsp decompiles it and save the generated `.java` in a cache folder (`.lsp/.cache/java/`)
- If the java class is from JDK (e.g. `java.util.UUID`), clojure-lsp tries a few things to be able to sucessfully find the definition, following this waterfall decision:
    - Most JRE installations contains the java source code in a `src.zip`, clojure-lsp tries to find it via `JAVA_HOME` or `java` command on `PATH`, if found clojure-lsp extracts to its global cache config (XDG_CONFIG or `~/.config/clojure-lsp`) to be used in other projects.
    - If `src.zip` is not found or user specified a custom `:java :jdk-source-uri`, clojure-lsp tries to download the source from that uri, only if `:java :download-jdk-source?` is enabled, disabled by default, extracting to the global cache dir later.

### Stub generation

It's possible to configure clojure-lsp to generate and analyze stubs for specific namespaces available on your project classpath, this is useful for closed source dependencies like `datomic.api`, with that clojure-lsp will be able to make most features work with those dependencies.
The available settings inside `:stubs` are:

- `:generation` for auto stubs generation:
    - `:namespaces` the namespaces to generate and analyze stubs, empty by default disabling stub generation.
    - `:output-dir` the output where to generate the stubs, by default `.lsp/.cache/stubs`
    - `java-command` the path to java command to spawn the stub process, default use `java` from `$PATH`.
- `:extra-dirs`, dirs to analyze to consider as part of manual generated stubs. Empty by default.

Example:

```clojure
{:stubs {:generation {:namespaces #{"datomic.api"}}}}
```

This should generate stubs for `datomic.api` namespace only on `.lsp/.cache/stubs` and clojure-lsp should analyze that during startup to provide completion, hover and other features.

Or to use manual generated stubs:

```clojure
{:stubs {:extra-dirs [".my-stubs"]}}
```

clojure-lsp will generate no stubs with that, but analyze that folder and consider it as manual generated stubs.
