-- A bot keeps the height it stands at to the hundredth (a bot on a 0.65 high furni stays at 0.65 after a reload),
-- as furni do. Widening only: every stored value keeps its meaning.

ALTER TABLE `bots` MODIFY `z` double(10,6) NOT NULL DEFAULT 0.000000;
