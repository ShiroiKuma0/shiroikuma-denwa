package org.fossify.phone.models

// a simpler Contact model containing just info needed at the call screen
//
// The first four are what every call has always had. The rest are the fields the caller block can be
// told to show (see helpers/CallScreenFields): they cost nothing extra to collect, since the lookup
// already reads whole contacts, and they default to "" for a caller with no contact behind them.
data class CallContact(
    var name: String,
    var photoUri: String,
    var number: String,
    var numberLabel: String,
    var prefix: String = "",
    var firstName: String = "",
    var middleName: String = "",
    var surname: String = "",
    var suffix: String = "",
    var nickname: String = "",
    var company: String = "",
    var jobPosition: String = "",
    var note: String = "",
)
