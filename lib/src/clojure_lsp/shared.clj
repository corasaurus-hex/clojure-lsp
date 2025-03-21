(ns clojure-lsp.shared
  (:require
   [clojure.core.async :refer [<! >! alts! chan go-loop timeout]]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as string]
   [lsp4clj.protocols.logger :as logger])
  (:import
   [java.net URI URL JarURLConnection URLDecoder]
   [java.nio.charset StandardCharsets]
   [java.nio.file Paths]))

(set! *warn-on-reflection* true)

(def ^:private ansi-colors
  {:reset "[0m"
   :red   "[31m"
   :green "[32m"
   :yellow "[33m"
   :cyan  "[36m"
   :bright-green "[92m"
   :bright-yellow "[93m"
   :bright-magenta "[35;1m"
   :bright-cyan "[36;1m"
   :bright-white "[37;1m"})

(defn colorize [s color]
  (str \u001b (ansi-colors color) s \u001b (ansi-colors :reset)))

(def startup-logger-color :green)
(def component-logger-color :bright-magenta)
(def feature-logger-color :bright-yellow)
(def task-logger-color :bright-cyan)

(defn deep-merge
  "Recursively merges maps together.
  Improved version of medley deep-merge concating colls instead of overwriting."
  ([])
  ([a] a)
  ([a b]
   (when (or a b)
     (letfn [(merge-entry [m e]
               (let [k  (key e)
                     v' (val e)]
                 (if (contains? m k)
                   (assoc m k (let [v (get m k)]
                                (cond
                                  (and (map? v) (map? v'))
                                  (deep-merge v v')

                                  (and (set? v) (set? v'))
                                  (set/union v v')

                                  (and (vector? v) (coll? v'))
                                  (vec (concat v v'))

                                  (and (coll? v) (coll? v'))
                                  (concat v v')

                                  :else v')))
                   (assoc m k v'))))]
       (reduce merge-entry (or a {}) (seq b)))))
  ([a b & more]
   (reduce deep-merge (or a {}) (cons b more))))

(defn file-exists? [^java.io.File f]
  (.exists f))

(defn directory? [^java.io.File f]
  (.isDirectory f))

(defn slurp-filename
  "Slurp filename f. Used for be able to with-refefs this function."
  [^String f]
  (slurp f))

(defn assoc-some
  "Assoc[iate] if the value is not nil. "
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v & kvs]
   (let [ret (assoc-some m k v)]
     (if kvs
       (if (next kvs)
         (recur ret (first kvs) (second kvs) (nnext kvs))
         (throw (IllegalArgumentException.
                  "assoc-some expects even number of arguments after map/vector, found odd number")))
       ret))))

(defn assoc-in-some
  [m ks v]
  (if (nil? v) m (assoc-in m ks v)))

(defn dissoc-in
  [m key-vec]
  (let [firsts (vec (butlast key-vec))
        node (dissoc (get-in m firsts) (last key-vec))]
    (assoc-in-some m firsts node)))

(def windows-os?
  (.contains (System/getProperty "os.name") "Windows"))

(defn uri->file-type [uri]
  (cond
    (string/ends-with? uri ".cljs") :cljs
    (string/ends-with? uri ".cljc") :cljc
    (string/ends-with? uri ".clj") :clj
    (string/ends-with? uri ".edn") :edn
    :else :unknown))

(defn uri->available-langs [uri]
  (cond
    (string/ends-with? uri ".cljs") #{:cljs}
    (string/ends-with? uri ".cljc") #{:clj :cljs}
    (string/ends-with? uri ".clj") #{:clj}
    (string/ends-with? uri ".edn") #{:edn}
    :else #{}))

(defn ^:private conform-uri
  [uri format-settings]
  (let [[match scheme+auth path] (re-matches #"([a-z:]+//.*?)(/.*)" uri)]
    (when-not match
      (logger/error "Found invalid URI:" uri))
    (str scheme+auth
         (-> path
             (string/replace-first #"^/[a-zA-Z](?::|%3A)/"
                                   (if (:upper-case-drive-letter? format-settings)
                                     string/upper-case
                                     string/lower-case))
             (cond-> (:encode-colons-in-path? format-settings)
               (string/replace ":" "%3A"))))))

(defn uri->path ^java.nio.file.Path [uri]
  (-> (conform-uri uri {:upper-case-drive-letter? true})
      URI. Paths/get))

(defn plain-uri? [uri]
  (when uri
    (or (string/starts-with? uri "file:/")
        (string/starts-with? uri "jar:file:/")
        (string/starts-with? uri "zipfile:/"))))

(defn ^:private uri-obj->filepath [uri]
  (-> uri Paths/get .toString
      (string/replace #"^[a-z]:\\" string/upper-case)))

(defn ^:private unescape-uri
  [^String uri]
  (try
    (URLDecoder/decode uri (.name StandardCharsets/UTF_8)) ;; compatible with Java 1.8 too!
    (catch UnsupportedOperationException _
      uri)
    (catch IllegalArgumentException _
      uri)))

(defn uri->filename
  "Converts a URI string into an absolute file path.

  The output path representation matches that of the operating system."
  [^String uri]
  (if (string/starts-with? uri "jar:")
    (let [unescaped-uri (unescape-uri uri)
          conn ^JarURLConnection (.openConnection (URL. unescaped-uri))
          jar-file (uri-obj->filepath ^URI (.toURI ^URL (.getJarFileURL conn)))]
      (str jar-file ":" (.getEntryName conn)))
    (let [uri-obj (URI. uri)
          [_ jar-uri-path nested-file] (when (= "zipfile" (.getScheme uri-obj))
                                         (re-find #"^(.*\.jar)::(.*)" (.getPath uri-obj)))]
      (if jar-uri-path
        (str (-> jar-uri-path io/file .getCanonicalPath) ":" nested-file)
        (uri-obj->filepath uri-obj)))))

(defn ensure-jarfile [uri db]
  (let [jar-scheme? (= "jar" (get @db [:settings :dependency-scheme]))]
    (if (or (not jar-scheme?)
            (string/starts-with? uri "jar:"))
      uri
      (-> uri
          (string/replace "zipfile:" "jar:file:")
          (string/replace "::" "!/")))))

(defn ^:private filepath->uri-obj ^URI [filepath]
  (-> filepath io/file .toPath .toUri))

(defn ^:private uri-encode [scheme path]
  (.toString (URI. scheme "" path nil)))

(def ^:private jar-file-with-filename-regex #"^(.*\.jar):(.*)")
(def ^:private jar-file-regex #"^(.*\.jar)")

(defn jar-file? [filename]
  (or (boolean (re-find jar-file-with-filename-regex filename))
      (boolean (re-find jar-file-regex filename))))

(def ^:private class-file-regex #"^(.*\.class)$")

(defn class-file? [uri]
  (boolean (re-find class-file-regex uri)))

(defn valid-uri? [^String value]
  (try
    (URL. value)
    true
    (catch Exception _
      false)))

(defn external-filename? [filename source-paths]
  (and filename
       (or (-> filename name jar-file?)
           (and (seq source-paths)
                (not-any? #(string/starts-with? filename %) source-paths)))))

(defn filename->uri
  "Converts an absolute file path into a file URI string.

  Jar files are given the `jar:file` or `zipfile` scheme depending on the
  `:dependency-scheme` setting."
  [^String filename db]
  (let [jar-scheme? (= "jar" (get-in @db [:settings :dependency-scheme]))
        [_ jar-filepath nested-file] (re-find jar-file-with-filename-regex filename)]
    (conform-uri
      (if-let [jar-uri-path (some-> jar-filepath (-> filepath->uri-obj .getPath))]
        (if jar-scheme?
          (uri-encode "jar:file" (str jar-uri-path "!/" nested-file))
          (uri-encode "zipfile" (str jar-uri-path "::" nested-file)))
        (.toString (filepath->uri-obj filename)))
      (get-in @db [:settings :uri-format]))))

(defn relativize-filepath
  "Returns absolute `path` (string) as relative file path starting at `root` (string)

  The output representation path matches that of the operating system."
  [path root]
  (let [path-obj (-> path io/file .toPath)]
    (if (.isAbsolute path-obj)
      (.toString (.relativize (-> root io/file .toPath) path-obj))
      path)))

(defn join-filepaths
  [& components]
  (.getPath ^java.io.File (apply io/file components)))

(defn namespace->uri [namespace source-paths filename db]
  (let [file-type (uri->file-type filename)]
    (filename->uri
      (join-filepaths (first (filter #(string/starts-with? filename %) source-paths))
                      (-> namespace
                          (string/replace "." (System/getProperty "file.separator"))
                          (string/replace "-" "_")
                          (str "." (name file-type))))
      db)))

(defn namespace+source-path->filename
  [namespace source-path file-type]
  (let [source-path-w-slash (if (string/ends-with? source-path (System/getProperty "file.separator"))
                              source-path
                              (str source-path (System/getProperty "file.separator")))]
    (str source-path-w-slash
         (-> namespace
             (string/replace #"\." (System/getProperty "file.separator"))
             (string/replace #"-" "_"))
         "."
         (name file-type))))

(defn ^:private path->folder-with-slash [^String path]
  (if (directory? (io/file path))
    (if (string/ends-with? path (System/getProperty "file.separator"))
      path
      (str path (System/getProperty "file.separator")))
    path))

(defn uri->namespace
  ([uri db]
   (uri->namespace uri (uri->filename uri) db))
  ([uri filename db]
   (let [project-root-uri (:project-root-uri @db)
         source-paths (get-in @db [:settings :source-paths])
         in-project? (when project-root-uri
                       (string/starts-with? uri project-root-uri))
         file-type (uri->file-type uri)]
     (when (and in-project? (not= :unknown file-type))
       (->> source-paths
            (keep (fn [source-path]
                    (when (string/starts-with? filename (path->folder-with-slash source-path))
                      (some-> (relativize-filepath filename source-path)
                              (->> (re-find #"^(.+)\.\S+$"))
                              (nth 1)
                              (string/replace (System/getProperty "file.separator") ".")
                              (string/replace #"_" "-")))))
            (reduce (fn [source-path-a source-path-b]
                      (cond
                        (not source-path-b) source-path-a
                        (not source-path-a) source-path-b
                        :else (if (> (count source-path-a)
                                     (count source-path-b))
                                source-path-b
                                source-path-a))) nil))))))

(defn filename->namespace [filename db]
  (uri->namespace (filename->uri filename db) filename db))

(defn inside?
  "Checks if element `a` is inside element `b` scope."
  [a b]
  (when (and a b)
    (let [a-name-row (:name-row a)
          a-name-col (:name-col a)
          b-name-row (:name-row b)
          b-name-col (:name-col b)
          b-end-row (or (:scope-end-row b)
                        (:end-row b)
                        (:name-end-row b))
          b-end-col (or (:scope-end-col b)
                        (:end-col b)
                        (:name-end-col b))]
      (and (or (< b-name-row a-name-row)
               (and (= b-name-row a-name-row)
                    (<= b-name-col a-name-col)))
           (or (< a-name-row b-end-row)
               (and (= a-name-row b-end-row)
                    (<= a-name-col b-end-col)))))))

(defn keywordize-first-depth
  [m]
  (into {}
        (for [[k v] m]
          [(keyword k) v])))

(defn position->line-column [position]
  [(inc (:line position))
   (inc (:character position))])

(defn debounce-all
  "Debounce in channel with ms miliseconds returning all values."
  [in ms]
  (let [out (chan)]
    (go-loop [old-values []
              last-val nil]
      (let [values (if (nil? last-val)
                     [(<! in)]
                     (conj old-values last-val))
            timer (timeout ms)
            [new-val ch] (alts! [in timer])]
        (condp = ch
          timer (do (>! out values) (recur old-values nil))
          in (recur values new-val))))
    out))

(defn debounce-by
  "Debounce in channel with ms miliseconds distincting by by-fn."
  [in ms by-fn]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val (if (nil? last-val) (<! in) last-val)
            timer (timeout ms)
            [new-val ch] (alts! [in timer])
            different? (and new-val
                            (not (= (by-fn val) (by-fn new-val))))]
        (cond
          different? (do (>! out val)
                         (>! out new-val)
                         (recur nil))
          (= ch timer) (do (>! out val)
                           (recur nil))
          (= ch in) (recur new-val))))
    out))

(defn to-file ^java.io.File
  [^java.nio.file.Path path
   ^String child]
  (.toFile (.resolve path child)))

(defn normalize-file
  ^java.io.File [^java.io.File file]
  (.toFile (.normalize (.toPath file))))

;; TODO move to a better place
(defn client-changes [changes db]
  (if (get-in @db [:client-capabilities :workspace :workspace-edit :document-changes])
    {:document-changes changes}
    {:changes (into {} (map (fn [{:keys [text-document edits]}]
                              [(:uri text-document) edits])
                            changes))}))

(defn clojure-lsp-version []
  (string/trim (slurp (io/resource "CLOJURE_LSP_VERSION"))))

(defn start-time->end-time-ms [start-time]
  (format "%.0fms" (float (/ (- (System/nanoTime) start-time) 1000000))))

(defmacro logging-time
  "Executes `body` logging `message` formatted with the time spent
  from body."
  [message & body]
  (let [start-sym (gensym "start-time")]
    `(let [~start-sym (System/nanoTime)
           result# (do ~@body)]
       ~(with-meta
          `(logger/info (format ~message (start-time->end-time-ms ~start-sym)))
          (meta &form))
       result#)))

(defmacro logging-results
  "Executes `body`, passing the results to `results-fn`, which should return a
  results message string. Logs `message` formatted with the time spent from body
  and the results message."
  [message results-fn & body]
  (let [start-sym (gensym "start-time")
        results-msg (gensym "results-msg")]
    `(let [~start-sym (System/nanoTime)
           result# (do ~@body)
           ~results-msg (~results-fn result#)]
       ~(with-meta
          `(logger/info (format ~message (start-time->end-time-ms ~start-sym) ~results-msg))
          (meta &form))
       result#)))

(defmacro logging-task [task-id & body]
  (let [msg (str (colorize task-id task-logger-color) " %s")]
    (with-meta `(logging-time ~msg ~@body) (meta &form))))

(defn ->range [{:keys [name-row name-end-row name-col name-end-col row end-row col end-col] :as element}]
  (when element
    {:start {:line (max 0 (dec (or name-row row))) :character (max 0 (dec (or name-col col)))}
     :end {:line (max 0 (dec (or name-end-row end-row))) :character (max 0 (dec (or name-end-col end-col)))}}))

(defn ->scope-range [{:keys [name-row name-end-row name-col name-end-col row end-row col end-col] :as element}]
  (when element
    {:start {:line (max 0 (dec (or row name-row))) :character (max 0 (dec (or col name-col)))}
     :end {:line (max 0 (dec (or end-row name-end-row))) :character (max 0 (dec (or end-col name-end-col)))}}))

(defn full-file-range []
  (->range {:row 1 :col 1 :end-row 1000000 :end-col 1000000}))
