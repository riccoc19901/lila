package lila.fishnet

import chess.format.Uci
import chess.format.pgn.SanStr
import chess.{ Position, MoveOrDrop, Ply, Replay }

import lila.analyse.{ Analysis, Info }
import lila.core.lilaism.LilaException

// Even though Info.variation is a List[SanStr]
// When we receive them from Fishnet clients it's actually a list of UCI moves.
// This converts them to San format. drops extra variations
private object UciToSan:

  type WithErrors[A] = (A, List[Exception])

  def apply(replay: Replay, analysis: Analysis): WithErrors[Analysis] =

    val pliesWithAdviceAndVariation: Set[Ply] = analysis.advices.view.collect {
      case a if a.info.hasVariation => a.ply
    }.toSet

    val onlyMeaningfulVariations: List[Info] = analysis.infos.map: info =>
      if pliesWithAdviceAndVariation(info.ply) then info
      else info.dropVariation

    def uciToSan(ply: Ply, variation: List[String]): Either[String, List[SanStr]] =
      for
        board <-
          if ply == replay.setup.startedAtPly + 1 then Right(replay.setup.board)
          else replay.moveAtPly(ply).map(_.boardBefore).toRight("No move found")
        ucis <- variation.traverse(Uci.apply).toRight(s"Invalid UCI moves $variation")
        moves <- ucis.foldM(board -> List.empty[MoveOrDrop]):
          case ((sit, moves), uci) => validateMove(moves, sit, ply, uci)
      yield moves._2.reverse.map(_.toSanStr)

    def validateMove(acc: List[MoveOrDrop], sit: Position, ply: Ply, uci: Uci) =
      uci(sit).bimap(e => s"ply $ply $e", move => move.boardAfter -> (move :: acc))

    onlyMeaningfulVariations.foldLeft[WithErrors[List[Info]]]((Nil, Nil)):
      case ((infos, errs), info) if info.variation.isEmpty => (info :: infos, errs)
      case ((infos, errs), info) =>
        uciToSan(info.ply, SanStr.raw(info.variation)).fold(
          err => (info.dropVariation :: infos, LilaException(err) :: errs),
          pgn => (info.copy(variation = pgn) :: infos, errs)
        )
    match
      case (infos, errors) => analysis.copy(infos = infos.reverse) -> errors
