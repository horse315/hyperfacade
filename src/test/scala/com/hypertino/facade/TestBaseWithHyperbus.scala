/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade

import com.hypertino.facade.workers.WsTestClientHelper
import monix.execution.Cancelable
import org.scalatest.BeforeAndAfterAll

abstract class TestBaseWithHyperbus(val configFileName: String = "inproc-test.conf",
                                    val ramlConfigFiles: Seq[String] = Seq("simple.raml")) extends TestBase with WsTestClientHelper with BeforeAndAfterAll {

  protected val testServices = new TestServices(configFileName, ramlConfigFiles, extraModule, false)

  import testServices._

  override def beforeEach(): Unit = {
    super.beforeEach()
    Thread.sleep(500)
  }

  override def afterEach(): Unit = {
    subscriptions.foreach(_.cancel())
    subscriptions.clear
  }

  override def afterAll(): Unit = {
    super.afterAll()
    testServices.close()
  }

  def register(s: Cancelable) = {
    testServices.subscriptions += s
    Thread.sleep(500)
  }

  def register(s: Seq[Cancelable]) = {
    testServices.subscriptions ++= s
    Thread.sleep(500)
  }
}
