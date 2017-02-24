# $1 input gdl file
# $2 output mck file
function compileMck() {
	echo
	echo "Constructing "$2
	java -jar deploy/MckTranslator.jar -i $1 -o $2 --use-prover
}

# $1 directory with input files
# $2 directory for output files
PWD=$(pwd)
echo PWD $PWD
GAME_DIR=$1
echo GAME_DIR $GAME_DIR
shift
OUT_DIR=$1
echo OUT_DIR $OUT_DIR
#cd $GAME_DIR
for f in $GAME_DIR*
do
	#cd $PWD
	compileMck $f $OUT_DIR$(basename $f)".mck"
	#cd $GAME_DIR
done
cd $PWD