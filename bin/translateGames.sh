ant jar
echo montyhall
java -jar deploy/MckTranslator.jar -i res/gdlii/MontyHall.gdl -o mh-prover.mck --use-prover
java -jar deploy/MckTranslator.jar -i res/gdlii/MontyHall.gdl -o mh-prover-define.mck --use-prover --use-define
mck mh-prover.mck
mck mh-prover-define.mck

echo kreigtictactoe
java -jar deploy/MckTranslator.jar -i res/gdlii/kriegtictactoe.gdl -o kttt-prover.mck --use-prover
java -jar deploy/MckTranslator.jar -i res/gdlii/kriegtictactoe.gdl -o kttt-prover-define.mck --use-prover --use-define
mck kttt-prover.mck
mck kttt-prover-define.mck

echo transit
java -jar deploy/MckTranslator.jar -i res/gdlii/transit.gdl -o trans-prover.mck --use-prover
java -jar deploy/MckTranslator.jar -i res/gdlii/transit.gdl -o trans-prover-define.mck --use-prover --use-define
mck trans-prover.mck
mck trans-prover-define.mck

echo meier
java -jar deploy/MckTranslator.jar -i res/gdlii/meier.gdl -o meier-prover.mck --use-prover
java -jar deploy/MckTranslator.jar -i res/gdlii/meier.gdl -o meier-prover-define.mck --use-prover --use-define
mck meier-prover.mck
mck meier-prover-define.mck
