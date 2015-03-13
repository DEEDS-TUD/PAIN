#!/usr/bin/perl
# GESTIONE ERRORI DEL PARSER DEL TOOL DI FAULT INJECTION
# Parametro 1: File di output del tool con gli errori
# Parametro 2: File .i su cui il tool ha lavorato

open(FILE, "<".$ARGV[0]) or die "\n\tErrore nell'apertura del file .txt di appoggio (Script err_parsel.pl)!!! \n\n";
local $/ = "\n";
$i=0;
while($linea = <FILE>){
	$file[$i++] = $linea;
}
close(FILE);
#$file = substr( @file[$#file], 0, index( @file[$#file], ':' ) );
#$riga = substr( @file[$#file], index( @file[$#file], ':' )+1 );
$file = substr( $file[0], 0, index( $file[0], ':' ) );
$riga = substr( $file[0], index( $file[0], ':' )+1 );
$pos1 = index( $riga, ':' );
$riga = substr( $riga, 0, $pos1 );

# ottenuto file di riferimento e riga passiamo alla modifica
open(FILE, "<".$ARGV[1]) or die "\n\t--> Errore nell'apertura del file .i (Script err_parsel.pl)!!! \n\n";
local $/ = "# ";
$i = 0;
@pzz;
$r_temp = 0;
while($linea = <FILE>){
	$pzz[$i] = $linea;
	if( $pzz[$i] =~ /^\d+\s\"$file/ ) {
		$temp = substr( $pzz[$i], 0, index( $pzz[$i], " " , 1 ) );
		if($temp <= $riga){
			if($temp > $r_temp){
				$r_temp = $temp;
				$r_temp_i = $i;
			}
		}
	}
	$i++;
}
close(FILE);
$linea = @pzz[$r_temp_i];
$indnl = 0;
$scorri = $riga-$r_temp+1;
for( $i = 0; $i < $scorri; $i++ ){
	$indnl = index( $linea, "\n", $indnl+1 );
}
$indnl++;
$str_temp = substr( $linea, $indnl );
$n = index( $str_temp, "\n" );
$rest = substr( $str_temp, $n+1 );
$str_temp = substr( $str_temp, 0, $n );

# Nel caso ci sia un'inizializzazione di una variabile non la perdiamo
if ( $str_temp =~ /^\s*(int|bool)\s\w+\s?=/ ) {
	$ugu = index( $str_temp, "=" );
	$str_temp = substr( $str_temp, 0, $ugu+1)." 0; //".substr( $str_temp, $ugu+2);
} else {
# idem nel caso di un while (che può provocare problemi nell'utilizzo del do{}while() );
	if( $str_temp =~ /^(\s*do)(.*typeof)(.*while.*);$/  ) {

		$str_temp = "//".$str_temp;

	} elsif( $str_temp =~ /asm\s+goto/  ) {

		$str_temp =~ s|asm\s+goto|//asm goto|;

		@rest_lines = split("\n",$rest);

		for $i(0..$#rest_lines){
			$rest_lines[$i] = "//".$rest_lines[$i];
			if($rest_lines[$i] =~ /\);/) {
				last;
			}
		}

		$rest = join("\n",@rest_lines);

	#} elsif( $str_temp =~ /^(.*while\s*\(+)(.*);$/  ) {
	#	$whi = index( $str_temp, "while" );
	#	$str_temp = substr( $str_temp, 0, $whi+6)." (false){ //".substr( $str_temp, $whi+7);
	} else {
		$str_temp = "//".$str_temp;
	}
}

@pzz[$r_temp_i] = substr( $linea, 0, $indnl ).$str_temp."\n".$rest;
# fatta la modifica ristampiamo tutto nel file originale!
open(FILE, "> ".$ARGV[1]) or die "\n\t--> Errore nell'apertura del file .i in scrittura (Script err_parsel.pl)!!! \n\n";
print FILE @pzz;
close(FILE);

exit 0;
