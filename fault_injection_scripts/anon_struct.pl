#!/usr/bin/perl
# Script per la rimozione di struct anonime (non conformi allo standard del compilatore)
# Parametro: File .i da processare

use Text::Balanced qw (  extract_multiple extract_bracketed );

open(FILE, "<".$ARGV[0]) or die "\n\t-> Errore nell'apertura del file da parte di anon_struct.pl!!! \n\n";
local $/ = "# 1 ";

while($linea = <FILE>){
	correggi($linea);
}

exit 0;

sub correggi
{
	my($linea, @pzz, $pre, $suf, $str);
	$linea = $_[0];
	@pzz = extract_multiple( $linea , [sub { extract_bracketed( $_[0], "{}", "struct " ) } ], undef, 0);
	if($pzz[0] ne $linea){

		my $i;
		for ($i = 0; $i <= $#pzz-1; $i = $i+2) {
			$pre = $pzz[$i];
			$str = $pzz[$i+1];
			$suf = $pzz[$i+2];
			if( ($suf =~ /^;/) && ($pre =~ /\bstruct\s*$/ ) ) {
				$pre =~ s/(struct\s*)$/\/\/$1/ ;
				$str =~ s/\}$/\/\/\}/ ;
			}
			print $pre;
			correggi($str);
		}
		print @pzz[$#pzz];
	} else {
		print $linea;
	}
}
