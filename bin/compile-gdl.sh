# $1 input gdl file
# $2 output mck file
function compileMck() {
	echo
	echo "Constructing "$2
	java -jar deploy/MckTranslator.jar -i $1 -o $2 --use-prover
}

PWD=$(pwd)
cd res/gdl
for f in *
do
	cd ../../
	compileMck "res/gdl/"$f "gdl.out/"$f".mck"
	cd res/gdl
done
cd PWD