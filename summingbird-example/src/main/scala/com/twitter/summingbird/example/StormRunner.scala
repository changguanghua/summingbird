/*
 Copyright 2013 Twitter, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.twitter.summingbird.example

import com.twitter.summingbird.storm.{ MergeableStoreSupplier, Storm }
import com.twitter.tormenta.spout.TwitterSpout
import twitter4j.TwitterStreamFactory
import twitter4j.conf.ConfigurationBuilder

/**
  * The following object contains code to execute the Summingbird
  * WordCount job defined in ExampleJob.scala on a local storm
  * cluster.
  */
object StormRunner {
  /**
    * These imports bring the requisite serialization injections, the
    * time extractor and the batcher into implicit scope. This is
    * required for the dependency injection pattern used by the
    * Summingbird Storm platform.
    */
  import Serialization._, StatusStreamer._

  /**
    * Configuration for Twitter4j. Configuration can also be managed
    * via a properties file, as described here:
    *
    * http://tugdualgrall.blogspot.com/2012/11/couchbase-create-large-dataset-using.html
    */
  lazy val config = new ConfigurationBuilder()
    .setOAuthConsumerKey("mykey")
    .setOAuthConsumerSecret("mysecret")
    .setOAuthAccessToken("token")
    .setOAuthAccessTokenSecret("tokensecret")
    .setJSONStoreEnabled(true) // required for JSON serialization
    .build

  /**
    * "spout" is a concrete Storm source for Status data. This will
    * act as the initial producer of Status instances in the
    * Summingbird word count job.
    */
  val spout = TwitterSpout(new TwitterStreamFactory(config))

  /**
    * And here's our MergeableStore supplier.
    *
    * A supplier is required (vs a bare store) because Storm
    * serializes every constructor parameter to its
    * "bolts". Serializing a live memcache store is a no-no, so the
    * Storm platform accepts a "supplier", essentially a function0
    * that when called will pop out a new instance of the required
    * store. This instance is cached when the bolt starts up and
    * starts merging tuples.
    *
    * A MergeableStore is a store that's aware of aggregation and
    * knows how to merge in new (K, V) pairs using a Monoid[V]. The
    * Monoid[Long] used by this particular store is being pulled in
    * from the Monoid companion object in Algebird. (Most trivial
    * Monoid instances will resolve this way.)
    */
  val storeSupplier: MergeableStoreSupplier[String, Long] =
    MergeableStoreSupplier.from(Memcache.mergeable("urlCount"))

  /**
    * When this main method is executed, Storm will begin running on a
    * separate thread on the local machine, pulling tweets off of the
    * TwitterSpout, generating and aggregating key-value pairs and
    * merging the incremental counts in the memcache store.
    *
    * Before running this code, make sure to start a local memcached
    * instance with "memcached". ("brew install memcached" will get
    * you all set up if you don't already have memcache installed
    * locally.)
    */
  def main(args: Array[String]) {
    Storm.local("wordCountJob").run(
      wordCount[Storm](spout, storeSupplier)
    )
  }
  /**
    * Once you've got this running in the background, fire up another
    * repl and query memcached for some counts.
    *
    * The following commands will look up words:

    {{{
    import com.twitter.summingbird.example._
    import com.twitter.util.Await
    val store = Memcache.mergeable("urlCount")

    def lookup(word: String) =
      Await.result(myStore.get((word, StatusStreamer.batcher.currentBatch)))

    lookup("and") // Or any other common word
    }}}
  */
}
