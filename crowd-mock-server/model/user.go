package model

import "encoding/xml"

// <user name="foo">
//   <first-name>Foo</first-name>
//   ...
//   <attributes>...</attributes>
// </user>
type UserEntity struct {
	XMLName     xml.Name       `xml:"user"`
	Name        string         `xml:"name,attr"`
	FirstName   string         `xml:"first-name,omitempty"`
	LastName    string         `xml:"last-name,omitempty"`
	DisplayName string         `xml:"display-name,omitempty"`
	Email       string         `xml:"email,omitempty"`
	Password    *PasswordCred  `xml:"password,omitempty"`
	Active      bool           `xml:"active"`
	CreatedDate int64          `xml:"created-date"`
	UpdatedDate int64          `xml:"updated-date"`
	Key         string         `xml:"key,omitempty"`
	Attributes  *AttributeList `xml:"attributes,omitempty"`
}

type PasswordCred struct {
	XMLName xml.Name `xml:"password"`
	Value   string   `xml:"value"`
}

type RenameRequest struct {
	XMLName xml.Name `xml:"rename"`
	NewName string   `xml:"new-name"`
}

// <users><user name="foo">...</user></users>
type UserEntityList struct {
	XMLName xml.Name     `xml:"users"`
	Users   []UserEntity `xml:"user"`
}
