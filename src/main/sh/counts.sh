#!/bin/bash --login
#$ -l h_rt=790000
#$ -j y
#$ -m a
#$ -M h.zoerner@campus.tu-berlin.de
#$ -o logfile/logfile_counts.log
#$ -cwd
#$ -pe mp 2
#$ -l mem_free=5G
#$ -N counts

date
hostname

jar="matsim-berlin-6.x-SNAPSHOT.jar"

arguments="prepare
           create-counts
           --network
           berlin-v5.5-network.xml.gz
           --shp
           ./shp/Verkehrsmengen_DTVw_2019.shp
           --output
           ./
           --input-crs
           EPSG:25833
           --target-crs
           EPSG:31468
           --road-type motorway
           --road-type trunk
           --road-type primary
           --road-type secondary
           --road-type tertiary
           --search-range 20"

command="java -jar -Xmx10G $jar $arguments"

echo ""
echo "command is $command"

echo ""
module add java/17
java -version

$command
