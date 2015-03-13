#!/usr/bin/perl
# GESTIONE ERRORI DEL TOOL DI FAULT INJECTION
# Parametro 1: File di output del tool con gli errori
# Parametro 2: File .i su cui il tool ha lavorato

open(FILE, "<".$ARGV[0]) or die "\n\t--> Errore nell'apertura del file .txt di appoggio (Script err_tool.pl)!!! \n\n";
local $/ = "\n";
$i=0;
$j=0;
$errflag=0;
while($linea = <FILE>){
	$file[++$i]=$linea;
	if(index($linea,'error:')>0){
		$errflag=1;
		if(index($linea,'error: array size must be nonnegative')>0){
			@arszerr[$j]=$i;
			$j++;
		}
	}
}
close(FILE);
if($errflag == 1){
	@pzz;
	foreach $ln (@arszerr) {
		$file = substr( @file[$ln], 0, index( @file[$ln], ':' ) );
		$riga = substr( @file[$ln], index( @file[$ln], ':' )+1 );
		$pos1 = index( $riga, ':' );
		$riga = substr( $riga, 0, $pos1 );
		# ottenuto file di riferimento e riga passiamo alla modifica
		open(FILE, "<".$ARGV[1]) or die "\n\t--> Errore nell'apertura del file .i (Script err_tool.pl)!!! \n\n";
		local $/ = "# ";
		$i=0;
		while($linea = <FILE>){
			$pzz[$i] = $linea;
			if( $pzz[$i] =~ /^\d+\s\"$file/ ) {
				$temp = substr( $pzz[$i], 0, index( $pzz[$i], " ", 1 ) );
				if($temp <= $riga){
					$r_temp = $temp;
					$r_temp_i = $i;
				}
			}
			$i++;
		}
		close(FILE);
		$linea = @pzz[$r_temp_i];
		$indnl = 0;
		for($i = 0; $i < $riga-$r_temp+1; $i++ ){
			$indnl = index($linea,"\n",$indnl+1 );
		}
		$indpar = index( $linea, "[", $indnl+1 );
		$str_temp = substr( $linea, $indpar );
		$n = index( $str_temp, "]" );
		$rest = substr( $str_temp, $n+1 );
		$str_temp = substr( $str_temp, 0, $n );
		@pzz[$r_temp_i] = substr( $linea, 0, $indpar )."[2]/*".$str_temp."]*/".$rest;
	}
	# fatta la modifica ristampiamo tutto nel file originale!
	if( $#arszerr +1 >0 ){
		open(FILE, "> ".$ARGV[1]) or die "\n\t--> Errore nell'apertura del file .i in scrittura (Script err_tool.pl)!!! \n\n";
		print FILE @pzz;
		close(FILE);
	} else {
		print "\t\t\tNon sono state fatte modifiche al file originale;\n\t\t\tse ci sono errori, non sono di tipo \"array size = -1\"\n";
	}
} else {
	print "\t\t\tIl tool non ha restituito errori\n";
}

exit 0;
