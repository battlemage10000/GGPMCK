# $1 name
# $2 file
# $3 args
function runMck() {
	name=$1
	shift
	file=$1
	shift
	args="$@"
	echo 
	echo $name
	mck $file $args
}


# $1 mck_dir
mck_dir=$1
for f in $mck_dir/* 
do
	runMck $(basename $f) $f --bmc=5
done
