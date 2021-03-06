/**
  * Copyright (c) 2019 BusyMachines
  *
  * See company homepage at: https://www.busymachines.com/
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package busymachines.pureharm.db

import busymachines.pureharm.phantom.PhantomType

/**
  *
  * @author Lorand Szakacs, https://github.com/lorandszakacs
  * @since 13 Jun 2019
  *
  */
package object test {
  object PhantomByte extends PhantomType[Byte]
  type PhantomByte = PhantomByte.Type

  object PhantomInt extends PhantomType[Int]
  type PhantomInt = PhantomInt.Type

  object PhantomLong extends PhantomType[Long]
  type PhantomLong = PhantomLong.Type

  object PhantomBigDecimal extends PhantomType[BigDecimal]
  type PhantomBigDecimal = PhantomBigDecimal.Type

  object PhantomString extends PhantomType[String]
  type PhantomString = PhantomString.Type

  object PhantomPK extends PhantomType[String]
  type PhantomPK = PhantomPK.Type

  object schema {
    val PureharmRows: TableName = TableName("pureharm_rows")
  }

}
