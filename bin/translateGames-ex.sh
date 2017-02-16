ant jar
startTime=$(date)
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
echo KTTT 5x5 mck
java -jar deploy/MckTranslator.jar -i res/gdlii/kriegTTT_5x5.gdl -o kttt55-prover.mck --use-prover
echo
echo KTTT 5x5 mck with define
java -jar deploy/MckTranslator.jar -i res/gdlii/kriegTTT_5x5.gdl -o kttt55-prover-define.mck --use-prover --use-define
echo
echo 3p Pacman mck
java -jar deploy/MckTranslator.jar -i res/gdlii/vis_pacman3p.gdl -o pac-prover.mck --use-prover
echo
echo 3p Pacman mck with define
java -jar deploy/MckTranslator.jar -i res/gdlii/vis_pacman3p.gdl -o pac-prover-define.mck --use-prover --use-define
echo
echo Backgammon mck
java -jar deploy/MckTranslator.jar -i res/gdlii/backgammon.gdl -o back-prover.mck --use-prover
echo
echo Backgammon mck with define
java -jar deploy/MckTranslator.jar -i res/gdlii/backgammon.gdl -o back-prover-define.mck --use-prover --use-define
echo
echo Mastermind mck
java -jar deploy/MckTranslator.jar -i res/gdlii/mastermind.gdl -o master-prover.mck --use-prover
echo
echo Mastermind mck with define
java -jar deploy/MckTranslator.jar -i res/gdlii/mastermind.gdl -o master-prover-define.mck --use-prover --use-define

echo
echo "Compile time: "$(startTime-$(date))
echo

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
echo
echo KTTT 5x5 mck
mck kttt55-prover.mck
echo
echo KTTT 5x5 mck with define
mck kttt55-prover-define.mck
echo
echo 3p Pacman mck
mck pac-prover.mck
echo
echo 3p Pacman mck with define
mck pac-prover-define.mck
echo
echo Backgammon mck
mck back-prover.mck
echo
echo Backgammon mck with define
mck back-prover-define.mck
echo
echo Mastermind mck
mck master-prover.mck
echo
echo Mastermind mck with define
mck master-prover-define.mck

echo
echo "Total time: "$(startTime-$(date))
echo
