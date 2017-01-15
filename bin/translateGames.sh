ant jar
echo
echo Monty Hall mck
java -jar deploy/MckTranslator.jar -i res/gdlii/MontyHall.gdl -o mh-prover.mck --use-prover
echo
echo Monty Hall mck with define
java -jar deploy/MckTranslator.jar -i res/gdlii/MontyHall.gdl -o mh-prover-define.mck --use-prover --use-define
echo
echo Kreig Tic Tac Toe mck
java -jar deploy/MckTranslator.jar -i res/gdlii/kriegtictactoe.gdl -o kttt-prover.mck --use-prover
echo
echo Kreig Tic Tac Toe mck with define
java -jar deploy/MckTranslator.jar -i res/gdlii/kriegtictactoe.gdl -o kttt-prover-define.mck --use-prover --use-define
echo
echo Transit mck
java -jar deploy/MckTranslator.jar -i res/gdlii/transit.gdl -o trans-prover.mck --use-prover
echo
echo Transit mck with define
java -jar deploy/MckTranslator.jar -i res/gdlii/transit.gdl -o trans-prover-define.mck --use-prover --use-define
echo
echo Meier mck
java -jar deploy/MckTranslator.jar -i res/gdlii/meier.gdl -o meier-prover.mck --use-prover
echo
echo Meier mck with define
java -jar deploy/MckTranslator.jar -i res/gdlii/meier.gdl -o meier-prover-define.mck --use-prover --use-define

echo
echo Monty Hall mck
mck mh-prover.mck
echo
echo Monty Hall mck with define
mck mh-prover-define.mck
echo
echo Kreig Tic Tac Toe mck
mck kttt-prover.mck
echo
echo Kreig Tic Tac Toe mck with define
mck kttt-prover-define.mck
echo
echo Transit mck
mck trans-prover.mck
echo
echo Transit mck with define
mck trans-prover-define.mck
echo
echo Meier mck
mck meier-prover.mck
echo
echo Meier mck with define
mck meier-prover-define.mck