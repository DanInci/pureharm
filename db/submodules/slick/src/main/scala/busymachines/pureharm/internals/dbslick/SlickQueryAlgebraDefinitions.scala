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
package busymachines.pureharm.internals.dbslick

import busymachines.pureharm.identifiable.Identifiable
import busymachines.pureharm.effects._
import busymachines.pureharm.db._
import busymachines.pureharm.dbslick._

/**
  * See [[busymachines.pureharm.dbslick.PureharmSlickDBProfile]]
  * On how to get to use this. Don't blame me, it's how you usually do stuff
  * with slick.
  *
  * @author Lorand Szakacs, https://github.com/lorandszakacs
  * @since 04 Apr 2019
  *
  */
trait SlickQueryAlgebraDefinitions {

  protected val enclosingProfile: slick.jdbc.JdbcProfile

  import enclosingProfile.api._
  import busymachines.pureharm.effects.implicits._
  import busymachines.pureharm.dbslick.instances._

  /**
    * @tparam E
    *   The type of value to be inserted into the table
    * @tparam PK
    *   The type of the value by which to uniquely identify the
    *   element `E`, by default this is also the primary key, and
    *   also has the unique constraint.
    */
  abstract class TableWithPK[E, PK](tag: Tag, name: TableName)(
    implicit val identifiable:           Identifiable[E, PK],
    implicit val columnTypePK:           ColumnType[PK],
  ) extends Table[E](tag, name) {
    //override if you want different constraints, like autoincrement
    def id: Rep[PK] = column(identifiable.fieldName, O.PrimaryKey, O.Unique)
  }

  //===========================================================================
  //===========================================================================
  //===========================================================================

  /**
    *
    * @tparam E
    *   The type of value to be inserted into the table
    * @tparam PK
    *   The type of the value by which to uniquely identify the
    *   element `E`, by default this is also the primary key, and
    *   also has the unique constraint.
    * @tparam TA
    *   Slick Table definition
    */
  abstract class SlickDAOQueryAlgebra[E, PK, TA <: TableWithPK[E, PK]](
    implicit val columnTypePK:   ColumnType[PK],
    implicit val identifiable:   Identifiable[E, PK],
    implicit val connectionIOEC: ConnectionIOEC,
  ) extends DAOAlgebra[ConnectionIO, E, PK] {

    /**
      * Because creating this object is done via a macro,
      * we have to actually override in each implementer and
      * call this with the explicit type for ``TA``
      * {{{
      *   class MyStringTable extends TableWithPK[String, String] {...}
      *   class MyQueries extends QueryAlgebra[String, String, MyStringTable]{
      *     override val dao = TableQuery[MyStringTable]
      *   }
      * }}}
      */
    def dao: TableQuery[TA]

    def find(pk: PK): ConnectionIO[Option[E]] = dao.filter(_.id === pk).result.headOption

    def retrieve(pk: PK)(implicit show: Show[PK]): ConnectionIO[E] =
      (dao.filter(_.id === pk).result.head: ConnectionIO[E]).adaptError {
        case NonFatal(e) => SlickDBEntryNotFoundAnomaly(pk.show, Option(e))
      }

    def insert(e: E): ConnectionIO[PK] = dao.+=(e).widenCIO.map(_ => eid(e))

    def insertMany(es: Iterable[E]): ConnectionIO[Unit] = dao.++=(es).widenCIO.void

    def update(e: E): ConnectionIO[E] = (dao.update(e): ConnectionIO[Int]).map(_ => e)

    def updateMany[M[_]: Traverse](es: M[E]): ConnectionIO[Unit] = es.traverse_((e: E) => update(e))

    def delete(pk: PK): ConnectionIO[Unit] = dao.filter(_.id === pk).delete.widenCIO.void

    def deleteMany(pks: Iterable[PK]): ConnectionIO[Unit] =
      dao.filter(_.id.inSet(pks)).delete.widenCIO.void

    def exists(pk: PK): ConnectionIO[Boolean] = dao.filter(_.id === pk).exists.result

    def existsAtLeastOne(pks: Iterable[PK]): ConnectionIO[Boolean] = dao.filter(_.id.inSet(pks)).exists.result

    def existAll(pks: Iterable[PK]): ConnectionIO[Boolean] =
      dao.filter(_.id.inSet(pks)).length.result.map(l => l == pks.size)

    private def eid(e: E): PK = identifiable.id(e)
  }

  object SlickDAOQueryAlgebra {

    def fromTableQuery[E, PK, TA <: TableWithPK[E, PK]](
      qt: TableQuery[TA],
    )(
      implicit
      columnTypePK:   ColumnType[PK],
      identifiable:   Identifiable[E, PK],
      connectionIOEC: ConnectionIOEC,
    ): SlickDAOQueryAlgebra[E, PK, TA] = {
      new SlickDAOQueryAlgebra[E, PK, TA]() {
        override val dao: TableQuery[TA] = qt
      }
    }
  }

  //===========================================================================
  //===========================================================================
  //===========================================================================

  abstract class SlickDAOAlgebra[F[_], E, PK, TA <: TableWithPK[E, PK]](
    implicit val transactor:     Transactor[F],
    implicit val columnTypePK:   ColumnType[PK],
    implicit val identifiable:   Identifiable[E, PK],
    implicit val connectionIOEC: ConnectionIOEC,
  ) extends DAOAlgebra[F, E, PK] {

    protected def queries: SlickDAOQueryAlgebra[E, PK, TA]

    override def find(pk: PK): F[Option[E]] = transactor.run(queries.find(pk))

    override def retrieve(pk: PK)(implicit show: Show[PK]): F[E] = transactor.run(queries.retrieve(pk))

    override def insert(e: E): F[PK] = transactor.run(queries.insert(e))

    override def insertMany(es: Iterable[E]): F[Unit] = transactor.run(queries.insertMany(es))

    override def update(e: E): F[E] = transactor.run(queries.update(e))

    override def updateMany[M[_]: Traverse](es: M[E]): F[Unit] = transactor.run(queries.updateMany(es))

    override def delete(pk: PK): F[Unit] = transactor.run(queries.delete(pk))

    override def deleteMany(pks: Iterable[PK]): F[Unit] = transactor.run(queries.deleteMany(pks))

    override def exists(pk: PK): F[Boolean] = transactor.run(queries.exists(pk))

    override def existsAtLeastOne(pks: Iterable[PK]): F[Boolean] = transactor.run(queries.existsAtLeastOne(pks))

    override def existAll(pks: Iterable[PK]): F[Boolean] = transactor.run(queries.existAll(pks))
  }

  object SlickDAOAlgebra {

    def fromQueryAlgebra[F[_], E, PK, TA <: TableWithPK[E, PK]](
      q: SlickDAOQueryAlgebra[E, PK, TA],
    )(
      implicit tr: Transactor[F],
    ): SlickDAOAlgebra[F, E, PK, TA] = {
      new SlickDAOAlgebra[F, E, PK, TA]()(
        transactor     = tr,
        columnTypePK   = q.columnTypePK,
        identifiable   = q.identifiable,
        connectionIOEC = q.connectionIOEC,
      ) {
        override protected val queries: SlickDAOQueryAlgebra[E, PK, TA] = q
      }
    }

    def fromTableQuery[F[_], E, PK, TA <: TableWithPK[E, PK]](
      qt: TableQuery[TA],
    )(
      implicit transactor: Transactor[F],
      columnTypePK:        ColumnType[PK],
      identifiable:        Identifiable[E, PK],
      connectionIOEC:      ConnectionIOEC,
    ): SlickDAOAlgebra[F, E, PK, TA] = {
      fromQueryAlgebra(SlickDAOQueryAlgebra.fromTableQuery(qt))
    }
  }
}
